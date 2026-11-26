package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.CustomerDto;
import com.andres.course.claude.springboot.springapi.app.dto.CustomerFilter;
import com.andres.course.claude.springboot.springapi.app.exceptions.DuplicateResourceException;
import com.andres.course.claude.springboot.springapi.app.exceptions.ResourceNotFoundException;
import com.andres.course.claude.springboot.springapi.app.mappers.CustomerMapper;
import com.andres.course.claude.springboot.springapi.app.models.Customer;
import com.andres.course.claude.springboot.springapi.app.repositories.CustomerRepository;
import com.andres.course.claude.springboot.springapi.app.repositories.CustomerSpecifications;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class CustomerServiceImpl implements CustomerService {

    /** Campos por los que se puede ordenar el listado ({@code ?sort=campo,asc|desc}). */
    static final Set<String> SORT_FIELDS = Set.of(
            "id", "name", "lastName", "email", "city", "birthDate", "active", "createdAt", "updatedAt");

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerMapper customerMapper;

    public CustomerServiceImpl(CustomerRepository customerRepository, InvoiceRepository invoiceRepository,
                               CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.customerMapper = customerMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerDto> findPage(CustomerFilter filter, Pageable pageable) {
        validateRanges(filter);
        return customerRepository.findAll(CustomerSpecifications.of(filter), Pageables.checked(pageable, SORT_FIELDS))
                .map(customerMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerDto> findAll(CustomerFilter filter, Sort sort) {
        validateRanges(filter);
        return customerRepository.findAll(CustomerSpecifications.of(filter), Pageables.checked(sort, SORT_FIELDS))
                .stream()
                .map(customerMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDto findById(Long id) {
        return customerMapper.toDto(getOrThrow(id));
    }

    @Override
    @Transactional
    public CustomerDto create(CustomerDto dto) {
        if (customerRepository.existsByEmail(dto.email())) {
            throw new DuplicateResourceException("Customer", "email", dto.email());
        }
        Customer saved = customerRepository.save(customerMapper.toEntity(dto));
        return customerMapper.toDto(saved);
    }

    @Override
    @Transactional
    public CustomerDto update(Long id, CustomerDto dto) {
        Customer entity = getOrThrow(id);
        // El email puede repetirse solo si pertenece al mismo cliente que se edita.
        customerRepository.findByEmail(dto.email())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("Customer", "email", dto.email());
                });
        customerMapper.updateEntity(dto, entity);
        return customerMapper.toDto(customerRepository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // Borrado lógico (spec 11): el cliente y sus facturas reciben la misma deleted_at en la misma
        // transacción; nada se borra de la base y las líneas se quedan con su factura. Desde aquí
        // @SQLRestriction los excluye de toda consulta (un cliente ya eliminado da 404).
        Customer customer = getOrThrow(id);
        LocalDateTime now = LocalDateTime.now();
        invoiceRepository.softDeleteByCustomerId(id, now);
        customer.setDeletedAt(now);
    }

    // Un rango con "desde" posterior a "hasta" no devolvería nada: se avisa con un 400.
    private void validateRanges(CustomerFilter filter) {
        if (filter == null) {
            return;
        }
        checkRange(filter.birthFrom(), filter.birthTo(), "birthFrom", "birthTo");
        checkRange(filter.createdFrom(), filter.createdTo(), "createdFrom", "createdTo");
    }

    private void checkRange(LocalDate from, LocalDate to, String fromName, String toName) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "El rango de fechas no es válido: '" + fromName + "' (" + from + ") es posterior a '"
                            + toName + "' (" + to + ")");
        }
    }

    private Customer getOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }
}

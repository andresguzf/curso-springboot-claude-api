package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.CustomerDto;
import com.andres.course.claude.springboot.springapi.app.exceptions.DuplicateResourceException;
import com.andres.course.claude.springboot.springapi.app.exceptions.ResourceNotFoundException;
import com.andres.course.claude.springboot.springapi.app.mappers.CustomerMapper;
import com.andres.course.claude.springboot.springapi.app.models.Customer;
import com.andres.course.claude.springboot.springapi.app.repositories.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public CustomerServiceImpl(CustomerRepository customerRepository, CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerDto> findAll() {
        return customerRepository.findAll()
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
        customerRepository.delete(getOrThrow(id));
    }

    private Customer getOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }
}

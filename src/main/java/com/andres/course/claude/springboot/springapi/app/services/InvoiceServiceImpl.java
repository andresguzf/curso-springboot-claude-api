package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.InvoiceDto;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceFilter;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceItemDto;
import com.andres.course.claude.springboot.springapi.app.exceptions.ResourceNotFoundException;
import com.andres.course.claude.springboot.springapi.app.mappers.InvoiceMapper;
import com.andres.course.claude.springboot.springapi.app.models.Customer;
import com.andres.course.claude.springboot.springapi.app.models.Invoice;
import com.andres.course.claude.springboot.springapi.app.models.InvoiceItem;
import com.andres.course.claude.springboot.springapi.app.repositories.CustomerRepository;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceRepository;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    /** Campos por los que se puede ordenar el listado ({@code ?sort=campo,asc|desc}). */
    static final Set<String> SORT_FIELDS = Set.of(
            "id", "invoiceNumber", "issueDate", "dueDate", "status", "total", "createdAt");

    /** IVA aplicado sobre el subtotal. */
    static final BigDecimal TAX_RATE = new BigDecimal("0.19");

    /** Máximo que admite una columna numeric(12,2): 10 enteros y 2 decimales. */
    static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    private static final String NUMBER_PREFIX = "FAC-";

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceMapper invoiceMapper;

    public InvoiceServiceImpl(InvoiceRepository invoiceRepository,
                              CustomerRepository customerRepository,
                              InvoiceMapper invoiceMapper) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.invoiceMapper = invoiceMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InvoiceDto> findPage(InvoiceFilter filter, Pageable pageable) {
        validateRange(filter);
        return invoiceRepository.findAll(InvoiceSpecifications.of(filter), Pageables.checked(pageable, SORT_FIELDS))
                .map(invoiceMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDto findById(Long id) {
        return invoiceMapper.toDto(getOrThrow(id));
    }

    @Override
    @Transactional
    public InvoiceDto create(InvoiceDto dto) {
        validateDates(dto);
        Invoice invoice = invoiceMapper.toEntity(dto);
        invoice.setCustomer(getCustomer(dto.customerId()));
        syncItems(invoice, dto.items());
        recalculate(invoice);
        invoice.setInvoiceNumber(nextNumber(invoice.getIssueDate()));
        return invoiceMapper.toDto(invoiceRepository.save(invoice));
    }

    @Override
    @Transactional
    public InvoiceDto update(Long id, InvoiceDto dto) {
        Invoice invoice = getOrThrow(id);
        validateDates(dto);
        // La API permite reasignar la factura a otro cliente (el dashboard no lo ofrece).
        if (!invoice.getCustomer().getId().equals(dto.customerId())) {
            invoice.setCustomer(getCustomer(dto.customerId()));
        }
        invoiceMapper.updateEntity(dto, invoice);
        syncItems(invoice, dto.items());
        recalculate(invoice);
        // saveAndFlush: el UPDATE se ejecuta ahora y @PreUpdate asigna updatedAt antes de armar la
        // respuesta (con save, el flush llegaría al hacer commit y la respuesta saldría sin la fecha).
        return invoiceMapper.toDto(invoiceRepository.saveAndFlush(invoice));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // Las líneas se borran con la factura (orphanRemoval y ON DELETE CASCADE en la base).
        invoiceRepository.delete(getOrThrow(id));
    }

    /**
     * Deja en la factura una línea por cada dto, en el mismo orden, actualizando las existentes
     * EN EL LUGAR: la línea en la posición i recibe los datos del dto i y conserva su número; las
     * sobrantes se quitan (orphanRemoval las borra) y las que faltan se agregan con el siguiente número.
     *
     * No se borra todo y se vuelve a insertar 1..n: al hacer flush Hibernate ejecuta los INSERT antes
     * que los DELETE, y la restricción UNIQUE (invoice_id, line_number) rechazaría el número repetido.
     * Por la misma razón no se renumeran las líneas que se conservan (si una factura tenía 1 y 3, la
     * nueva será la 4): en una factura nueva, las líneas quedan 1..n.
     */
    private void syncItems(Invoice invoice, List<InvoiceItemDto> dtos) {
        List<InvoiceItem> current = new ArrayList<>(invoice.getItems());
        int lastNumber = current.stream().mapToInt(InvoiceItem::getLineNumber).max().orElse(0);
        for (int i = 0; i < dtos.size(); i++) {
            if (i < current.size()) {
                invoiceMapper.updateItem(dtos.get(i), current.get(i));
            } else {
                InvoiceItem item = new InvoiceItem();
                invoiceMapper.updateItem(dtos.get(i), item);
                item.setLineNumber(++lastNumber);
                invoice.addItem(item);
            }
        }
        for (int i = dtos.size(); i < current.size(); i++) {
            invoice.removeItem(current.get(i));
        }
    }

    // subtotal = suma de las líneas; IVA = 19 % del subtotal; total = subtotal + IVA.
    // Todo con BigDecimal y redondeo HALF_UP a 2 decimales, como round() en PostgreSQL.
    private void recalculate(Invoice invoice) {
        BigDecimal subtotal = invoice.getItems().stream()
                .map(InvoiceItem::calculateLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tax);
        if (total.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("El total de la factura no puede superar " + MAX_AMOUNT.toPlainString());
        }
        invoice.setSubtotal(subtotal);
        invoice.setTax(tax);
        invoice.setTotal(total);
    }

    /**
     * Siguiente número para el año de emisión: FAC-2026-0001, FAC-2026-0002... Se busca el último con
     * ese prefijo (orden alfabético, válido mientras el correlativo tenga 4 dígitos). Dos altas
     * simultáneas pueden calcular el mismo número: el UNIQUE de la base rechaza la segunda (409).
     */
    private String nextNumber(LocalDate issueDate) {
        String prefix = NUMBER_PREFIX + issueDate.getYear() + "-";
        int next = invoiceRepository.findTopByInvoiceNumberStartingWithOrderByInvoiceNumberDesc(prefix)
                .map(last -> Integer.parseInt(last.getInvoiceNumber().substring(prefix.length())) + 1)
                .orElse(1);
        return prefix + String.format("%04d", next);
    }

    // El CHECK de la base también lo impide, pero así el error es un 400 con un mensaje claro.
    private void validateDates(InvoiceDto dto) {
        if (dto.dueDate() != null && dto.dueDate().isBefore(dto.issueDate())) {
            throw new IllegalArgumentException("La fecha de vencimiento (" + dto.dueDate()
                    + ") no puede ser anterior a la fecha de emisión (" + dto.issueDate() + ")");
        }
    }

    // Un rango con "desde" posterior a "hasta" no devolvería nada: se avisa con un 400.
    private void validateRange(InvoiceFilter filter) {
        if (filter != null && filter.issuedFrom() != null && filter.issuedTo() != null
                && filter.issuedFrom().isAfter(filter.issuedTo())) {
            throw new IllegalArgumentException("El rango de fechas no es válido: 'issuedFrom' (" + filter.issuedFrom()
                    + ") es posterior a 'issuedTo' (" + filter.issuedTo() + ")");
        }
    }

    private Customer getCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    }

    private Invoice getOrThrow(Long id) {
        return invoiceRepository.findWithCustomerAndItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
    }
}

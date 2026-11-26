package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.InvoiceDto;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceFilter;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceItemDto;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceStatsDto;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceStatsDto.CustomerTotal;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceStatsDto.MonthTotal;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceStatsDto.StatusTotal;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceStatsFilter;
import com.andres.course.claude.springboot.springapi.app.exceptions.ResourceNotFoundException;
import com.andres.course.claude.springboot.springapi.app.mappers.InvoiceMapper;
import com.andres.course.claude.springboot.springapi.app.models.Customer;
import com.andres.course.claude.springboot.springapi.app.models.Invoice;
import com.andres.course.claude.springboot.springapi.app.models.InvoiceItem;
import com.andres.course.claude.springboot.springapi.app.models.InvoiceStatus;
import com.andres.course.claude.springboot.springapi.app.repositories.CustomerRepository;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceRepository;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceSpecifications;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceStatsViews.CountTotalView;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceStatsViews.MonthStatusTotalView;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceStatsViews.StatusTotalView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** Estados que suman a los importes (las anuladas no). */
    private static final List<InvoiceStatus> BILLED_STATUSES = List.of(InvoiceStatus.PAID, InvoiceStatus.PENDING);

    private static final int STATS_MONTHS = 12;
    /** Máximo de meses en la serie mensual con un rango (más barras no se leen). */
    static final int MAX_STATS_MONTHS = 24;
    private static final int TOP_CUSTOMERS = 5;

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceMapper invoiceMapper;
    // "Hoy" para las vencidas y los 12 meses: en la zona del servidor (TZ=America/Santiago en Docker).
    // Inyectable para que los tests fijen la fecha.
    private final Clock clock;

    // Dos constructores: @Autowired marca el que usa Spring; el otro (con Clock) es para los tests.
    @Autowired
    public InvoiceServiceImpl(InvoiceRepository invoiceRepository,
                              CustomerRepository customerRepository,
                              InvoiceMapper invoiceMapper) {
        this(invoiceRepository, customerRepository, invoiceMapper, Clock.systemDefaultZone());
    }

    InvoiceServiceImpl(InvoiceRepository invoiceRepository,
                       CustomerRepository customerRepository,
                       InvoiceMapper invoiceMapper,
                       Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.invoiceMapper = invoiceMapper;
        this.clock = clock;
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

    @Override
    @Transactional(readOnly = true)
    public InvoiceStatsDto stats(InvoiceStatsFilter filter) {
        InvoiceStatsFilter f = filter == null ? InvoiceStatsFilter.empty() : filter;
        LocalDate today = LocalDate.now(clock);
        // Primero los meses de la serie: valida el rango (orden y máximo) antes de consultar.
        List<YearMonth> months = statsMonths(f, today);
        // El filtro del resumen son las mismas Specifications del listado (cliente y rango de emisión).
        Specification<Invoice> spec = InvoiceSpecifications.forStats(f);

        // Por estado: siempre los 3, aunque alguno no tenga facturas.
        Map<InvoiceStatus, StatusTotalView> found = new EnumMap<>(InvoiceStatus.class);
        invoiceRepository.totalsByStatus(spec).forEach(view -> found.put(view.status(), view));
        List<StatusTotal> byStatus = Arrays.stream(InvoiceStatus.values())
                .map(status -> found.containsKey(status)
                        ? new StatusTotal(status, found.get(status).count(), money(found.get(status).total()))
                        : new StatusTotal(status, 0, money(null)))
                .toList();
        long invoiceCount = byStatus.stream().mapToLong(StatusTotal::count).sum();
        BigDecimal paid = totalOf(byStatus, InvoiceStatus.PAID);
        BigDecimal pending = totalOf(byStatus, InvoiceStatus.PENDING);

        CountTotalView overdue = invoiceRepository.dueBefore(spec, InvoiceStatus.PENDING, today);

        return new InvoiceStatsDto(invoiceCount, paid.add(pending), paid, pending,
                money(overdue.total()), overdue.count(), byStatus, monthly(spec, months), topCustomers(spec));
    }

    /**
     * Meses de la serie mensual, del más antiguo al más nuevo (spec 12):
     * sin rango, los 12 hasta el actual; solo "desde", de ese mes al actual (o a ese mes si es futuro);
     * solo "hasta", los 12 que terminan en ese mes; con ambos, de un mes al otro.
     *
     * @throws IllegalArgumentException si "desde" es posterior a "hasta" o la serie supera 24 meses (400)
     */
    static List<YearMonth> statsMonths(InvoiceStatsFilter filter, LocalDate today) {
        LocalDate from = filter.issuedFrom();
        LocalDate to = filter.issuedTo();
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("El rango de fechas no es válido: 'issuedFrom' (" + from
                    + ") es posterior a 'issuedTo' (" + to + ")");
        }
        YearMonth current = YearMonth.from(today);
        YearMonth last;
        YearMonth first;
        if (from == null && to == null) {
            last = current;
            first = current.minusMonths(STATS_MONTHS - 1L);
        } else if (to == null) {
            first = YearMonth.from(from);
            last = first.isAfter(current) ? first : current;
        } else if (from == null) {
            last = YearMonth.from(to);
            first = last.minusMonths(STATS_MONTHS - 1L);
        } else {
            first = YearMonth.from(from);
            last = YearMonth.from(to);
        }
        long count = first.until(last, ChronoUnit.MONTHS) + 1;
        if (count > MAX_STATS_MONTHS) {
            throw new IllegalArgumentException("El rango no puede abarcar más de " + MAX_STATS_MONTHS
                    + " meses (abarca " + count + ")");
        }
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }

    // Importe por mes de la serie (sin anuladas); un mes sin facturas va en 0.
    private List<MonthTotal> monthly(Specification<Invoice> spec, List<YearMonth> months) {
        LocalDate from = months.getFirst().atDay(1);
        LocalDate to = months.getLast().atEndOfMonth();
        Map<YearMonth, Map<InvoiceStatus, BigDecimal>> totals = new HashMap<>();
        for (MonthStatusTotalView view : invoiceRepository.totalsByMonth(spec, from, to, BILLED_STATUSES)) {
            totals.computeIfAbsent(YearMonth.of(view.year(), view.month()), month -> new EnumMap<>(InvoiceStatus.class))
                    .put(view.status(), money(view.total()));
        }
        return months.stream()
                .map(month -> {
                    Map<InvoiceStatus, BigDecimal> ofMonth = totals.getOrDefault(month, Map.of());
                    return new MonthTotal(month.toString(),
                            ofMonth.getOrDefault(InvoiceStatus.PAID, money(null)),
                            ofMonth.getOrDefault(InvoiceStatus.PENDING, money(null)));
                })
                .toList();
    }

    private List<CustomerTotal> topCustomers(Specification<Invoice> spec) {
        return invoiceRepository.topCustomers(spec, BILLED_STATUSES, TOP_CUSTOMERS).stream()
                .map(view -> new CustomerTotal(view.customerId(), view.name() + " " + view.lastName(),
                        view.invoiceCount(), money(view.total())))
                .toList();
    }

    private static BigDecimal totalOf(List<StatusTotal> byStatus, InvoiceStatus status) {
        return byStatus.stream().filter(total -> total.status() == status).findFirst().orElseThrow().total();
    }

    // Importes siempre con 2 decimales (una suma sin filas llega como 0 sin decimales o null).
    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
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
     * ese prefijo, incluidas las facturas eliminadas (orden alfabético, válido mientras el correlativo
     * tenga 4 dígitos). Dos altas simultáneas pueden calcular el mismo número: el UNIQUE de la base
     * rechaza la segunda (409).
     */
    private String nextNumber(LocalDate issueDate) {
        String prefix = NUMBER_PREFIX + issueDate.getYear() + "-";
        int next = invoiceRepository.findLastNumberWithPrefix(prefix)
                .map(last -> Integer.parseInt(last.substring(prefix.length())) + 1)
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

package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.InvoiceDto;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceFilter;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceItemDto;
import com.andres.course.claude.springboot.springapi.app.exceptions.ResourceNotFoundException;
import com.andres.course.claude.springboot.springapi.app.mappers.InvoiceMapper;
import com.andres.course.claude.springboot.springapi.app.models.Customer;
import com.andres.course.claude.springboot.springapi.app.models.Invoice;
import com.andres.course.claude.springboot.springapi.app.models.InvoiceItem;
import com.andres.course.claude.springboot.springapi.app.models.InvoiceStatus;
import com.andres.course.claude.springboot.springapi.app.repositories.CustomerRepository;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    private static final LocalDate ISSUE = LocalDate.of(2026, 11, 20);

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private CustomerRepository customerRepository;

    private InvoiceServiceImpl invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceServiceImpl(invoiceRepository, customerRepository, new InvoiceMapper());
    }

    private static Customer customer(long id) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setName("Ana");
        customer.setLastName("Pérez");
        customer.setEmail("ana@ejemplo.com");
        return customer;
    }

    private static InvoiceItemDto item(String description, String quantity, String unitPrice) {
        return new InvoiceItemDto(null, description, new BigDecimal(quantity), new BigDecimal(unitPrice), null);
    }

    private static InvoiceDto request(Long customerId, LocalDate issueDate, LocalDate dueDate, InvoiceStatus status,
                                      List<InvoiceItemDto> items) {
        return new InvoiceDto(null, null, customerId, null, issueDate, dueDate, status, "  Soporte  ", items,
                null, null, null, null, null);
    }

    // Factura guardada con líneas numeradas como se indica (p. ej. 1, 3 si la 2 se borró antes).
    private static Invoice stored(long id, int... lineNumbers) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setInvoiceNumber("FAC-2026-0007");
        invoice.setCustomer(customer(1L));
        invoice.setIssueDate(ISSUE);
        invoice.setStatus(InvoiceStatus.PAID);
        for (int number : lineNumbers) {
            InvoiceItem item = new InvoiceItem();
            item.setId(100L + number);
            item.setLineNumber(number);
            item.setDescription("Línea " + number);
            item.setQuantity(BigDecimal.ONE);
            item.setUnitPrice(new BigDecimal("1000.00"));
            invoice.addItem(item);
        }
        return invoice;
    }

    private void saveReturnsArgument() {
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createNumeraLasLineasCalculaLosImportesYAsignaElSiguienteNumero() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L)));
        Invoice last = new Invoice();
        last.setInvoiceNumber("FAC-2026-0007");
        when(invoiceRepository.findTopByInvoiceNumberStartingWithOrderByInvoiceNumberDesc("FAC-2026-"))
                .thenReturn(Optional.of(last));
        saveReturnsArgument();

        InvoiceDto created = invoiceService.create(request(1L, ISSUE, null, null, List.of(
                item("Desarrollo (horas)", "20", "4000"),
                item("Taller (horas)", "1.5", "33.33"))));

        assertThat(created.invoiceNumber()).isEqualTo("FAC-2026-0008");
        assertThat(created.customerName()).isEqualTo("Ana Pérez");
        assertThat(created.status()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(created.notes()).isEqualTo("Soporte");
        assertThat(created.items()).extracting(InvoiceItemDto::lineNumber).containsExactly(1, 2);
        // 1,5 × 33,33 = 49,995 → 50,00 (HALF_UP, como round() de PostgreSQL)
        assertThat(created.items()).extracting(InvoiceItemDto::lineTotal)
                .containsExactly(new BigDecimal("80000.00"), new BigDecimal("50.00"));
        // subtotal 80 050,00; IVA 19 % = 15 209,50; total 95 259,50
        assertThat(created.subtotal()).isEqualByComparingTo("80050.00");
        assertThat(created.tax()).isEqualByComparingTo("15209.50");
        assertThat(created.total()).isEqualByComparingTo("95259.50");
    }

    @Test
    void elIvaSeRedondeaAMedioArriba() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L)));
        saveReturnsArgument();

        // 100,05 × 0,19 = 19,0095 → 19,01
        InvoiceDto created = invoiceService.create(request(1L, ISSUE, null, null, List.of(item("Servicio", "1", "100.05"))));

        assertThat(created.tax()).isEqualTo(new BigDecimal("19.01"));
        assertThat(created.total()).isEqualTo(new BigDecimal("119.06"));
    }

    @Test
    void elPrimerNumeroDelAnoEsElCeroCeroCeroUnoYUsaElAnoDeEmision() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L)));
        when(invoiceRepository.findTopByInvoiceNumberStartingWithOrderByInvoiceNumberDesc("FAC-2027-"))
                .thenReturn(Optional.empty());
        saveReturnsArgument();

        InvoiceDto created = invoiceService.create(request(1L, LocalDate.of(2027, 1, 2), null, null,
                List.of(item("Servicio", "1", "1000"))));

        assertThat(created.invoiceNumber()).isEqualTo("FAC-2027-0001");
    }

    @Test
    void createConClienteInexistenteResponde404SinGuardar() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.create(request(99L, ISSUE, null, null, List.of(item("A", "1", "1")))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer con id 99 no encontrado");
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void unVencimientoAnteriorALaEmisionSeRechaza() {
        assertThatThrownBy(() -> invoiceService.create(request(1L, ISSUE, ISSUE.minusDays(1), null,
                List.of(item("A", "1", "1")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede ser anterior a la fecha de emisión");
        verifyNoInteractions(invoiceRepository, customerRepository);
    }

    @Test
    void unTotalMayorQueElMaximoDeLaColumnaSeRechaza() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L)));

        // 99 999 999,99 × 100 = 9 999 999 999,00 (cabe en la línea), pero con el 19 % supera 9 999 999 999,99
        assertThatThrownBy(() -> invoiceService.create(request(1L, ISSUE, null, null,
                List.of(item("Mucho", "99999999.99", "100")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede superar");
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void updateDeTresADosLineasActualizaEnElLugarYQuitaLaUltima() {
        Invoice invoice = stored(5L, 1, 2, 3);
        InvoiceItem first = invoice.getItems().get(0);
        when(invoiceRepository.findWithCustomerAndItemsById(5L)).thenReturn(Optional.of(invoice));
        saveReturnsArgument();

        InvoiceDto updated = invoiceService.update(5L, request(1L, ISSUE, null, null, List.of(
                item("Nueva 1", "2", "500"),
                item("Nueva 2", "1", "300"))));

        assertThat(invoice.getItems()).hasSize(2);
        assertThat(invoice.getItems().get(0)).isSameAs(first);
        assertThat(updated.items()).extracting(InvoiceItemDto::lineNumber).containsExactly(1, 2);
        assertThat(updated.items()).extracting(InvoiceItemDto::description).containsExactly("Nueva 1", "Nueva 2");
        assertThat(updated.subtotal()).isEqualByComparingTo("1300.00");
        // Sin estado en el request se conserva el actual; el número no cambia.
        assertThat(updated.status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(updated.invoiceNumber()).isEqualTo("FAC-2026-0007");
        verify(invoiceRepository, never()).findTopByInvoiceNumberStartingWithOrderByInvoiceNumberDesc(anyString());
    }

    @Test
    void updateDeDosACuatroLineasAgregaConLosNumerosSiguientes() {
        when(invoiceRepository.findWithCustomerAndItemsById(5L)).thenReturn(Optional.of(stored(5L, 1, 2)));
        saveReturnsArgument();

        InvoiceDto updated = invoiceService.update(5L, request(1L, ISSUE, null, InvoiceStatus.CANCELLED, List.of(
                item("A", "1", "1"), item("B", "1", "1"), item("C", "1", "1"), item("D", "1", "1"))));

        assertThat(updated.items()).extracting(InvoiceItemDto::lineNumber).containsExactly(1, 2, 3, 4);
        assertThat(updated.status()).isEqualTo(InvoiceStatus.CANCELLED);
    }

    @Test
    void updateNoRenumeraLasLineasQueSeConservan() {
        // Una factura con las líneas 1 y 3 (la 2 se borró antes): la línea nueva es la 4, no la 3.
        when(invoiceRepository.findWithCustomerAndItemsById(5L)).thenReturn(Optional.of(stored(5L, 1, 3)));
        saveReturnsArgument();

        InvoiceDto updated = invoiceService.update(5L, request(1L, ISSUE, null, null, List.of(
                item("A", "1", "1"), item("B", "1", "1"), item("C", "1", "1"))));

        assertThat(updated.items()).extracting(InvoiceItemDto::lineNumber).containsExactly(1, 3, 4);
    }

    @Test
    void updateConOtroClienteLoReasigna() {
        when(invoiceRepository.findWithCustomerAndItemsById(5L)).thenReturn(Optional.of(stored(5L, 1)));
        Customer other = customer(2L);
        other.setName("Bruno");
        other.setLastName("González");
        when(customerRepository.findById(2L)).thenReturn(Optional.of(other));
        saveReturnsArgument();

        InvoiceDto updated = invoiceService.update(5L, request(2L, ISSUE, null, null, List.of(item("A", "1", "1"))));

        assertThat(updated.customerId()).isEqualTo(2L);
        assertThat(updated.customerName()).isEqualTo("Bruno González");
    }

    @Test
    void updateDeUnaFacturaInexistenteResponde404() {
        when(invoiceRepository.findWithCustomerAndItemsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.update(99L, request(1L, ISSUE, null, null, List.of(item("A", "1", "1")))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Invoice con id 99 no encontrado");
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void deleteBorraLaFacturaO404() {
        Invoice invoice = stored(5L, 1);
        when(invoiceRepository.findWithCustomerAndItemsById(5L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.findWithCustomerAndItemsById(99L)).thenReturn(Optional.empty());

        invoiceService.delete(5L);

        verify(invoiceRepository).delete(invoice);
        assertThatThrownBy(() -> invoiceService.delete(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findPageAgregaElDesempatePorIdYDevuelveDtos() {
        when(invoiceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(stored(5L, 1)), invocation.getArgument(1), 1));

        Page<InvoiceDto> page = invoiceService.findPage(InvoiceFilter.empty(),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "issueDate")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(invoiceRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getSort()).containsExactly(Sort.Order.desc("issueDate"), Sort.Order.asc("id"));
        assertThat(page.getContent()).singleElement()
                .satisfies(dto -> assertThat(dto.invoiceNumber()).isEqualTo("FAC-2026-0007"));
    }

    @Test
    void findPageRechazaUnRangoInvertidoYUnOrdenNoPermitido() {
        InvoiceFilter inverted = new InvoiceFilter(null, null, null, ISSUE, ISSUE.minusDays(1));
        assertThatThrownBy(() -> invoiceService.findPage(inverted, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'issuedFrom'");

        assertThatThrownBy(() -> invoiceService.findPage(InvoiceFilter.empty(), PageRequest.of(0, 10, Sort.by("notes"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No se puede ordenar por 'notes'");
        verifyNoInteractions(invoiceRepository);
    }
}

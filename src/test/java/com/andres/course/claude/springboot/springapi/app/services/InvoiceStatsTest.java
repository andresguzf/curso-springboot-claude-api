package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.InvoiceStatsDto;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceStatsDto.MonthTotal;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceStatsDto.StatusTotal;
import com.andres.course.claude.springboot.springapi.app.mappers.InvoiceMapper;
import com.andres.course.claude.springboot.springapi.app.models.InvoiceStatus;
import com.andres.course.claude.springboot.springapi.app.repositories.CustomerRepository;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceRepository;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceStatsViews.CountTotalView;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceStatsViews.CustomerTotalView;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceStatsViews.MonthStatusTotalView;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceStatsViews.StatusTotalView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Estadísticas de facturación (spec 11): InvoiceServiceImpl.stats() con el repositorio simulado. */
@ExtendWith(MockitoExtension.class)
class InvoiceStatsTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final List<InvoiceStatus> BILLED = List.of(InvoiceStatus.PAID, InvoiceStatus.PENDING);

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private CustomerRepository customerRepository;

    private InvoiceServiceImpl invoiceService;

    // Proyecciones simuladas: records que implementan las interfaces de InvoiceStatsViews.
    record Status(InvoiceStatus getStatus, long getCount, BigDecimal getTotal) implements StatusTotalView {
    }

    record Month(int getYear, int getMonth, InvoiceStatus getStatus, BigDecimal getTotal) implements MonthStatusTotalView {
    }

    record CountTotal(long getCount, BigDecimal getTotal) implements CountTotalView {
    }

    record Customer(Long getCustomerId, String getName, String getLastName, long getInvoiceCount, BigDecimal getTotal)
            implements CustomerTotalView {
    }

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(12, 0).atZone(ZONE).toInstant(), ZONE);
        invoiceService = new InvoiceServiceImpl(invoiceRepository, customerRepository, new InvoiceMapper(), clock);
    }

    @Test
    void sumaLosImportesSinAnuladasYCompletaLosTresEstados() {
        when(invoiceRepository.totalsByStatus()).thenReturn(List.of(
                new Status(InvoiceStatus.PAID, 3, new BigDecimal("500000.00")),
                new Status(InvoiceStatus.CANCELLED, 1, new BigDecimal("142800.00"))));
        when(invoiceRepository.dueBefore(InvoiceStatus.PENDING, TODAY)).thenReturn(new CountTotal(0, null));
        when(invoiceRepository.totalsByMonthSince(any(), any())).thenReturn(List.of());
        when(invoiceRepository.topCustomers(any(), any())).thenReturn(List.of());

        InvoiceStatsDto stats = invoiceService.stats();

        assertThat(stats.invoiceCount()).isEqualTo(4);
        assertThat(stats.paid()).isEqualTo(new BigDecimal("500000.00"));
        assertThat(stats.pending()).isEqualTo(new BigDecimal("0.00"));
        // Las anuladas cuentan en invoiceCount y byStatus, no en el facturado.
        assertThat(stats.billed()).isEqualTo(new BigDecimal("500000.00"));
        assertThat(stats.overdue()).isEqualTo(new BigDecimal("0.00"));
        assertThat(stats.byStatus()).extracting(StatusTotal::status)
                .containsExactly(InvoiceStatus.PENDING, InvoiceStatus.PAID, InvoiceStatus.CANCELLED);
        assertThat(stats.byStatus().getFirst()).isEqualTo(new StatusTotal(InvoiceStatus.PENDING, 0, new BigDecimal("0.00")));
    }

    @Test
    void devuelveDoceMesesHastaElActualConCeroDondeNoHayFacturas() {
        when(invoiceRepository.totalsByStatus()).thenReturn(List.of());
        when(invoiceRepository.dueBefore(InvoiceStatus.PENDING, TODAY))
                .thenReturn(new CountTotal(2, new BigDecimal("172550.00")));
        when(invoiceRepository.totalsByMonthSince(eq(LocalDate.of(2025, 12, 1)), eq(BILLED))).thenReturn(List.of(
                new Month(2026, 10, InvoiceStatus.PAID, new BigDecimal("390000.00")),
                new Month(2026, 11, InvoiceStatus.PENDING, new BigDecimal("172550.00")),
                new Month(2026, 11, InvoiceStatus.PAID, new BigDecimal("51170"))));
        when(invoiceRepository.topCustomers(any(), any())).thenReturn(List.of());

        InvoiceStatsDto stats = invoiceService.stats();

        assertThat(stats.monthly()).hasSize(12);
        assertThat(stats.monthly().getFirst()).isEqualTo(new MonthTotal("2025-12", new BigDecimal("0.00"), new BigDecimal("0.00")));
        assertThat(stats.monthly().get(10)).isEqualTo(new MonthTotal("2026-10", new BigDecimal("390000.00"), new BigDecimal("0.00")));
        assertThat(stats.monthly().getLast())
                .isEqualTo(new MonthTotal("2026-11", new BigDecimal("51170.00"), new BigDecimal("172550.00")));
        assertThat(stats.overdueCount()).isEqualTo(2);
        assertThat(stats.overdue()).isEqualTo(new BigDecimal("172550.00"));
    }

    @Test
    void topCincoClientesConNombreCompletoYSinAnuladas() {
        when(invoiceRepository.totalsByStatus()).thenReturn(List.of());
        when(invoiceRepository.dueBefore(InvoiceStatus.PENDING, TODAY)).thenReturn(new CountTotal(0, null));
        when(invoiceRepository.totalsByMonthSince(any(), any())).thenReturn(List.of());
        when(invoiceRepository.topCustomers(eq(BILLED), eq(Limit.of(5)))).thenReturn(List.of(
                new Customer(3L, "Bruno", "González", 1, new BigDecimal("285600.00")),
                new Customer(1L, "Ana", "Pérez", 2, new BigDecimal("279650.00"))));

        InvoiceStatsDto stats = invoiceService.stats();

        assertThat(stats.topCustomers()).extracting(InvoiceStatsDto.CustomerTotal::customerName)
                .containsExactly("Bruno González", "Ana Pérez");
        assertThat(stats.topCustomers().get(1).invoiceCount()).isEqualTo(2);
        verify(invoiceRepository).topCustomers(BILLED, Limit.of(5));
    }
}

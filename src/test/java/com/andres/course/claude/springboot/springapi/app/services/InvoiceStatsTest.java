package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.InvoiceStatsDto;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceStatsDto.MonthTotal;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceStatsDto.StatusTotal;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceStatsFilter;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Estadísticas de facturación (specs 11 y 12): InvoiceServiceImpl.stats(filter) con el repositorio simulado. */
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

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(12, 0).atZone(ZONE).toInstant(), ZONE);
        invoiceService = new InvoiceServiceImpl(invoiceRepository, customerRepository, new InvoiceMapper(), clock);
    }

    // Sin filas por defecto (lenient: cada test reemplaza solo la respuesta que comprueba).
    private void emptyRepository() {
        lenient().when(invoiceRepository.totalsByStatus(any())).thenReturn(List.of());
        lenient().when(invoiceRepository.dueBefore(any(), eq(InvoiceStatus.PENDING), eq(TODAY))).thenReturn(new CountTotalView(0L, null));
        lenient().when(invoiceRepository.totalsByMonth(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(invoiceRepository.topCustomers(any(), any(), eq(5))).thenReturn(List.of());
    }

    private static InvoiceStatsFilter range(LocalDate from, LocalDate to) {
        return new InvoiceStatsFilter(null, from, to);
    }

    @Test
    void sumaLosImportesSinAnuladasYCompletaLosTresEstados() {
        emptyRepository();
        when(invoiceRepository.totalsByStatus(any())).thenReturn(List.of(
                new StatusTotalView(InvoiceStatus.PAID, 3L, new BigDecimal("500000.00")),
                new StatusTotalView(InvoiceStatus.CANCELLED, 1L, new BigDecimal("142800.00"))));

        InvoiceStatsDto stats = invoiceService.stats(InvoiceStatsFilter.empty());

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
    void sinRangoDevuelveDoceMesesHastaElActualConCeroDondeNoHayFacturas() {
        emptyRepository();
        when(invoiceRepository.dueBefore(any(), eq(InvoiceStatus.PENDING), eq(TODAY)))
                .thenReturn(new CountTotalView(2L, new BigDecimal("172550.00")));
        when(invoiceRepository.totalsByMonth(any(), eq(LocalDate.of(2025, 12, 1)), eq(LocalDate.of(2026, 11, 30)), eq(BILLED)))
                .thenReturn(List.of(
                        new MonthStatusTotalView(2026, 10, InvoiceStatus.PAID, new BigDecimal("390000.00")),
                        new MonthStatusTotalView(2026, 11, InvoiceStatus.PENDING, new BigDecimal("172550.00")),
                        new MonthStatusTotalView(2026, 11, InvoiceStatus.PAID, new BigDecimal("51170"))));

        InvoiceStatsDto stats = invoiceService.stats(InvoiceStatsFilter.empty());

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
        emptyRepository();
        when(invoiceRepository.topCustomers(any(), eq(BILLED), eq(5))).thenReturn(List.of(
                new CustomerTotalView(3L, "Bruno", "González", 1L, new BigDecimal("285600.00")),
                new CustomerTotalView(1L, "Ana", "Pérez", 2L, new BigDecimal("279650.00"))));

        InvoiceStatsDto stats = invoiceService.stats(InvoiceStatsFilter.empty());

        assertThat(stats.topCustomers()).extracting(InvoiceStatsDto.CustomerTotal::customerName)
                .containsExactly("Bruno González", "Ana Pérez");
        assertThat(stats.topCustomers().get(1).invoiceCount()).isEqualTo(2);
    }

    @Test
    void conRangoLaSerieVaDelMesDesdeAlMesHastaYLaConsultaSeAcotaAEsosMeses() {
        emptyRepository();

        InvoiceStatsDto stats = invoiceService.stats(new InvoiceStatsFilter(1L, LocalDate.of(2026, 3, 15), LocalDate.of(2026, 5, 10)));

        assertThat(stats.monthly()).extracting(MonthTotal::month).containsExactly("2026-03", "2026-04", "2026-05");
        verify(invoiceRepository).totalsByMonth(any(), eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 5, 31)), eq(BILLED));
    }

    @Test
    void losMesesSegunElRango() {
        // Solo "desde": de ese mes al actual.
        assertThat(InvoiceServiceImpl.statsMonths(range(LocalDate.of(2026, 9, 10), null), TODAY))
                .containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11));
        // Solo "desde" en el futuro: solo ese mes.
        assertThat(InvoiceServiceImpl.statsMonths(range(LocalDate.of(2027, 2, 1), null), TODAY))
                .containsExactly(YearMonth.of(2027, 2));
        // Solo "hasta": los 12 meses que terminan en ese mes.
        assertThat(InvoiceServiceImpl.statsMonths(range(null, LocalDate.of(2026, 6, 30)), TODAY))
                .hasSize(12).startsWith(YearMonth.of(2025, 7)).endsWith(YearMonth.of(2026, 6));
        // Un solo día: un mes. 24 meses justos: válido.
        assertThat(InvoiceServiceImpl.statsMonths(range(TODAY, TODAY), TODAY)).containsExactly(YearMonth.of(2026, 11));
        assertThat(InvoiceServiceImpl.statsMonths(range(LocalDate.of(2024, 12, 1), LocalDate.of(2026, 11, 30)), TODAY))
                .hasSize(24);
    }

    @Test
    void unRangoInvertidoOMayorADosAnosResponde400SinConsultar() {
        assertThatThrownBy(() -> invoiceService.stats(range(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'issuedFrom' (2026-05-01) es posterior a 'issuedTo' (2026-04-01)");
        assertThatThrownBy(() -> invoiceService.stats(range(LocalDate.of(2024, 11, 1), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El rango no puede abarcar más de 24 meses (abarca 25)");
        verifyNoInteractions(invoiceRepository);
    }
}

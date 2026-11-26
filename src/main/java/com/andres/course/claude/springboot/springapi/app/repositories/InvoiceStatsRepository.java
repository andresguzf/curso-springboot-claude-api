package com.andres.course.claude.springboot.springapi.app.repositories;

import com.andres.course.claude.springboot.springapi.app.models.Invoice;
import com.andres.course.claude.springboot.springapi.app.models.InvoiceStatus;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceStatsViews.CountTotalView;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceStatsViews.CustomerTotalView;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceStatsViews.MonthStatusTotalView;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceStatsViews.StatusTotalView;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Agregados de las estadísticas del resumen con un filtro opcional (spec 12). Fragmento de
 * {@link InvoiceRepository}: lo implementa {@link InvoiceStatsRepositoryImpl} con Criteria API, porque
 * {@code JpaSpecificationExecutor} no hace {@code group by} y un {@code @Query} con
 * {@code (:param is null or ...)} falla en PostgreSQL con parámetros nulos (constitución).
 * El {@code filter} son las mismas Specifications del listado, así ambos cuentan lo mismo.
 */
public interface InvoiceStatsRepository {

    List<StatusTotalView> totalsByStatus(Specification<Invoice> filter);

    /** Importe por mes y estado, con fecha de emisión entre {@code from} y {@code to} (inclusive). */
    List<MonthStatusTotalView> totalsByMonth(Specification<Invoice> filter, LocalDate from, LocalDate to,
                                             Collection<InvoiceStatus> statuses);

    /** Facturas en ese estado con vencimiento anterior a {@code today}. */
    CountTotalView dueBefore(Specification<Invoice> filter, InvoiceStatus status, LocalDate today);

    /** Clientes con más facturado (en esos estados), de mayor a menor, hasta {@code limit}. */
    List<CustomerTotalView> topCustomers(Specification<Invoice> filter, Collection<InvoiceStatus> statuses, int limit);
}

package com.andres.course.claude.springboot.springapi.app.dto;

import com.andres.course.claude.springboot.springapi.app.models.InvoiceStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Estadísticas de facturación para el resumen ({@code GET /api/invoices/stats}). Solo respuesta: no
 * es un recurso que se cree o edite, así que no lleva validación. Las facturas anuladas cuentan en
 * {@code invoiceCount} y en {@code byStatus}, pero no en los importes; las eliminadas (borrado lógico)
 * no cuentan en nada.
 *
 * @param invoiceCount número de facturas (todos los estados)
 * @param billed       facturado: pagadas + pendientes
 * @param paid         total de las pagadas
 * @param pending      total de las pendientes
 * @param overdue      total de las pendientes con vencimiento anterior a hoy
 * @param overdueCount cuántas pendientes están vencidas
 * @param byStatus     cantidad e importe por estado (siempre los 3 estados)
 * @param monthly      últimos 12 meses por mes de emisión, del más antiguo al actual (meses sin facturas en 0)
 * @param topCustomers hasta 5 clientes con más facturado (sin anuladas), de mayor a menor
 */
public record InvoiceStatsDto(
        long invoiceCount,
        BigDecimal billed,
        BigDecimal paid,
        BigDecimal pending,
        BigDecimal overdue,
        long overdueCount,
        List<StatusTotal> byStatus,
        List<MonthTotal> monthly,
        List<CustomerTotal> topCustomers
) {

    /** Cantidad e importe de las facturas en un estado. */
    public record StatusTotal(InvoiceStatus status, long count, BigDecimal total) {
    }

    /** Facturado en un mes ("2026-11"), separado en pagado y pendiente. */
    public record MonthTotal(String month, BigDecimal paid, BigDecimal pending) {
    }

    /** Un cliente con su facturado (sin anuladas). */
    public record CustomerTotal(Long customerId, String customerName, long invoiceCount, BigDecimal total) {
    }
}

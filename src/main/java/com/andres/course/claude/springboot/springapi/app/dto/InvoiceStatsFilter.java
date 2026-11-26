package com.andres.course.claude.springboot.springapi.app.dto;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

import java.time.LocalDate;

/**
 * Filtros de las estadísticas del resumen ({@code GET /api/invoices/stats?customerId=&issuedFrom=&issuedTo=}).
 *
 * Como {@link InvoiceFilter}, no es un DTO de recurso: agrupa los query params opcionales y Spring MVC lo
 * enlaza por constructor. Cada campo en null significa "sin filtro"; el rango de emisión es inclusivo.
 *
 * @param customerId facturas de un cliente (uno inexistente o eliminado deja todo en cero)
 * @param issuedFrom fecha de emisión desde (yyyy-MM-dd)
 * @param issuedTo   fecha de emisión hasta (yyyy-MM-dd)
 */
public record InvoiceStatsFilter(
        Long customerId,
        @DateTimeFormat(iso = ISO.DATE) LocalDate issuedFrom,
        @DateTimeFormat(iso = ISO.DATE) LocalDate issuedTo
) {

    /** Sin filtros: todas las facturas, últimos 12 meses en la serie mensual. */
    public static InvoiceStatsFilter empty() {
        return new InvoiceStatsFilter(null, null, null);
    }
}

package com.andres.course.claude.springboot.springapi.app.dto;

import com.andres.course.claude.springboot.springapi.app.models.InvoiceStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

import java.time.LocalDate;

/**
 * Parámetros de búsqueda del listado de facturas
 * ({@code GET /api/invoices?search=&customerId=&status=&issuedFrom=&issuedTo=}).
 *
 * No es un DTO de recurso: agrupa los query params opcionales para pasarlos juntos al service
 * y a {@code InvoiceSpecifications.of(filter)}. Spring MVC lo enlaza por constructor.
 * Cada campo en null significa "sin filtro"; el rango de fechas es inclusivo.
 *
 * @param search     texto en el número de factura o en el nombre, apellido o email del cliente
 * @param customerId facturas de un cliente
 * @param status     PENDING, PAID o CANCELLED
 * @param issuedFrom fecha de emisión desde (yyyy-MM-dd)
 * @param issuedTo   fecha de emisión hasta (yyyy-MM-dd)
 */
public record InvoiceFilter(
        String search,
        Long customerId,
        InvoiceStatus status,
        @DateTimeFormat(iso = ISO.DATE) LocalDate issuedFrom,
        @DateTimeFormat(iso = ISO.DATE) LocalDate issuedTo
) {

    /** Filtro vacío: devuelve todas las facturas. */
    public static InvoiceFilter empty() {
        return new InvoiceFilter(null, null, null, null, null);
    }
}

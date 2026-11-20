package com.andres.course.claude.springboot.springapi.app.dto;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

import java.time.LocalDate;

/**
 * Parámetros de búsqueda del listado de clientes ({@code GET /api/customers?search=&active=&...}).
 *
 * No es un DTO de recurso: agrupa los query params opcionales para pasarlos juntos al service
 * y a {@code CustomerSpecifications.of(filter)}. Spring MVC lo enlaza por constructor.
 * Cada campo en null significa "sin filtro"; los rangos de fecha son inclusivos.
 *
 * @param search      texto en nombre, apellido, email o ciudad
 * @param active      true = activos, false = inactivos
 * @param city        texto contenido en la ciudad
 * @param birthFrom   fecha de nacimiento desde (yyyy-MM-dd)
 * @param birthTo     fecha de nacimiento hasta (yyyy-MM-dd)
 * @param createdFrom fecha de alta desde (yyyy-MM-dd)
 * @param createdTo   fecha de alta hasta (yyyy-MM-dd, incluye todo ese día)
 */
public record CustomerFilter(
        String search,
        Boolean active,
        String city,
        @DateTimeFormat(iso = ISO.DATE) LocalDate birthFrom,
        @DateTimeFormat(iso = ISO.DATE) LocalDate birthTo,
        @DateTimeFormat(iso = ISO.DATE) LocalDate createdFrom,
        @DateTimeFormat(iso = ISO.DATE) LocalDate createdTo
) {

    /** Filtro vacío: devuelve todos los clientes. */
    public static CustomerFilter empty() {
        return new CustomerFilter(null, null, null, null, null, null, null);
    }
}

package com.andres.course.claude.springboot.springapi.app.dto;

import com.andres.course.claude.springboot.springapi.app.models.Role;

/**
 * Parámetros de búsqueda del listado de usuarios ({@code GET /api/users?search=&role=&enabled=}).
 *
 * No es un DTO de recurso: agrupa los query params opcionales para pasarlos juntos al service
 * y a {@code UserSpecifications.of(filter)}. Spring MVC lo enlaza por constructor.
 * Cada campo en null significa "sin filtro".
 *
 * @param search  texto contenido en el email
 * @param role    USER o ADMIN
 * @param enabled true = habilitados, false = deshabilitados
 */
public record UserFilter(
        String search,
        Role role,
        Boolean enabled
) {

    /** Filtro vacío: devuelve todos los usuarios. */
    public static UserFilter empty() {
        return new UserFilter(null, null, null);
    }
}

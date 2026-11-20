package com.andres.course.claude.springboot.springapi.app.repositories;

import com.andres.course.claude.springboot.springapi.app.dto.UserFilter;
import com.andres.course.claude.springboot.springapi.app.models.Role;
import com.andres.course.claude.springboot.springapi.app.models.User;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import static com.andres.course.claude.springboot.springapi.app.repositories.LikePatterns.ESCAPE;

/**
 * Filtros dinámicos del listado de usuarios. Cada filtro sin valor no restringe nada,
 * así se combinan solo los que vienen en la petición (mismo enfoque que CustomerSpecifications).
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    /** Texto contenido (sin distinguir mayúsculas) en el email. */
    public static Specification<User> emailContains(String search) {
        return (root, query, cb) -> StringUtils.hasText(search)
                ? cb.like(cb.lower(root.get("email")), LikePatterns.contains(search), ESCAPE)
                : cb.conjunction();
    }

    /** Usuarios con ese rol; null no filtra. */
    public static Specification<User> hasRole(Role role) {
        return (root, query, cb) -> role == null ? cb.conjunction() : cb.equal(root.get("role"), role);
    }

    /** Usuarios habilitados (true) o deshabilitados (false); null no filtra. */
    public static Specification<User> hasEnabled(Boolean enabled) {
        return (root, query, cb) -> enabled == null ? cb.conjunction() : cb.equal(root.get("enabled"), enabled);
    }

    /** Combina con AND todos los filtros del record; los que vienen en null no restringen nada. */
    public static Specification<User> of(UserFilter filter) {
        UserFilter f = filter == null ? UserFilter.empty() : filter;
        return Specification.allOf(
                emailContains(f.search()),
                hasRole(f.role()),
                hasEnabled(f.enabled()));
    }
}

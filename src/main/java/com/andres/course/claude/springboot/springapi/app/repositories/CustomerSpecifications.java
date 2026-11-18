package com.andres.course.claude.springboot.springapi.app.repositories;

import com.andres.course.claude.springboot.springapi.app.models.Customer;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Filtros dinámicos del listado de clientes. Cada filtro sin valor no restringe nada,
 * así se combinan solo los que vienen en la petición.
 *
 * Se usan Specifications y no un @Query con "(:param is null or ...)": en PostgreSQL ese patrón
 * falla con parámetros nulos (Hibernate no sabe su tipo y termina en errores como lower(bytea)).
 */
public final class CustomerSpecifications {

    private static final char ESCAPE = '\\';

    private CustomerSpecifications() {
    }

    /** Texto contenido (sin distinguir mayúsculas) en nombre, apellido, email o ciudad. */
    public static Specification<Customer> matches(String search) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(search)) {
                return cb.conjunction();
            }
            String pattern = "%" + escapeLike(search.trim().toLowerCase()) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, ESCAPE),
                    cb.like(cb.lower(root.get("lastName")), pattern, ESCAPE),
                    cb.like(cb.lower(root.get("email")), pattern, ESCAPE),
                    cb.like(cb.lower(root.get("city")), pattern, ESCAPE));
        };
    }

    /** Clientes activos (true) o inactivos (false); null no filtra. */
    public static Specification<Customer> hasActive(Boolean active) {
        return (root, query, cb) -> active == null ? cb.conjunction() : cb.equal(root.get("active"), active);
    }

    // Escapa los comodines de LIKE para que "%" o "_" en el texto se busquen literalmente.
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

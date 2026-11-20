package com.andres.course.claude.springboot.springapi.app.repositories;

import com.andres.course.claude.springboot.springapi.app.dto.CustomerFilter;
import com.andres.course.claude.springboot.springapi.app.models.Customer;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.andres.course.claude.springboot.springapi.app.repositories.LikePatterns.ESCAPE;

/**
 * Filtros dinámicos del listado de clientes. Cada filtro sin valor no restringe nada,
 * así se combinan solo los que vienen en la petición.
 *
 * Se usan Specifications y no un @Query con "(:param is null or ...)": en PostgreSQL ese patrón
 * falla con parámetros nulos (Hibernate no sabe su tipo y termina en errores como lower(bytea)).
 */
public final class CustomerSpecifications {

    private CustomerSpecifications() {
    }

    /** Texto contenido (sin distinguir mayúsculas) en nombre, apellido, email o ciudad. */
    public static Specification<Customer> matches(String search) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(search)) {
                return cb.conjunction();
            }
            String pattern = LikePatterns.contains(search);
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

    /** Texto contenido (sin distinguir mayúsculas) en la ciudad. */
    public static Specification<Customer> cityContains(String city) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(city)) {
                return cb.conjunction();
            }
            return cb.like(cb.lower(root.get("city")), LikePatterns.contains(city), ESCAPE);
        };
    }

    /** Fecha de nacimiento dentro del rango [from, to]; cualquiera de los extremos puede ser null. */
    public static Specification<Customer> birthDateBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            Path<LocalDate> birthDate = root.get("birthDate");
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(birthDate, from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(birthDate, to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Fecha de alta dentro del rango [from, to], ambos inclusivos; cualquiera puede ser null.
     * createdAt es LocalDateTime: "hasta" se compara con el inicio del día siguiente para
     * incluir todo el último día.
     */
    public static Specification<Customer> createdBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            Path<LocalDateTime> createdAt = root.get("createdAt");
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(createdAt, from.atStartOfDay()));
            }
            if (to != null) {
                predicates.add(cb.lessThan(createdAt, to.plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Combina con AND todos los filtros del record; los que vienen en null no restringen nada. */
    public static Specification<Customer> of(CustomerFilter filter) {
        CustomerFilter f = filter == null ? CustomerFilter.empty() : filter;
        return Specification.allOf(
                matches(f.search()),
                hasActive(f.active()),
                cityContains(f.city()),
                birthDateBetween(f.birthFrom(), f.birthTo()),
                createdBetween(f.createdFrom(), f.createdTo()));
    }
}

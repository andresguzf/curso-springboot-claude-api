package com.andres.course.claude.springboot.springapi.app.repositories;

import com.andres.course.claude.springboot.springapi.app.dto.InvoiceFilter;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceStatsFilter;
import com.andres.course.claude.springboot.springapi.app.models.Customer;
import com.andres.course.claude.springboot.springapi.app.models.Invoice;
import com.andres.course.claude.springboot.springapi.app.models.InvoiceStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.andres.course.claude.springboot.springapi.app.repositories.LikePatterns.ESCAPE;

/**
 * Filtros dinámicos del listado de facturas. Cada filtro sin valor no restringe nada, así se
 * combinan solo los que vienen en la petición (Specifications, no @Query: ver CustomerSpecifications).
 */
public final class InvoiceSpecifications {

    private InvoiceSpecifications() {
    }

    /** Texto contenido (sin distinguir mayúsculas) en el número o en el nombre, apellido o email del cliente. */
    public static Specification<Invoice> matches(String search) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(search)) {
                return cb.conjunction();
            }
            String pattern = LikePatterns.contains(search);
            Join<Invoice, Customer> customer = root.join("customer");
            return cb.or(
                    cb.like(cb.lower(root.get("invoiceNumber")), pattern, ESCAPE),
                    cb.like(cb.lower(customer.get("name")), pattern, ESCAPE),
                    cb.like(cb.lower(customer.get("lastName")), pattern, ESCAPE),
                    cb.like(cb.lower(customer.get("email")), pattern, ESCAPE));
        };
    }

    /** Facturas de un cliente; null no filtra. Compara la FK, sin join. */
    public static Specification<Invoice> hasCustomer(Long customerId) {
        return (root, query, cb) -> customerId == null
                ? cb.conjunction()
                : cb.equal(root.get("customer").get("id"), customerId);
    }

    /** Facturas en ese estado; null no filtra. */
    public static Specification<Invoice> hasStatus(InvoiceStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    /** Fecha de emisión dentro del rango [from, to]; cualquiera de los extremos puede ser null. */
    public static Specification<Invoice> issuedBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            Path<LocalDate> issueDate = root.get("issueDate");
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(issueDate, from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(issueDate, to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Combina con AND todos los filtros del record; los que vienen en null no restringen nada. */
    public static Specification<Invoice> of(InvoiceFilter filter) {
        InvoiceFilter f = filter == null ? InvoiceFilter.empty() : filter;
        return Specification.allOf(
                matches(f.search()),
                hasCustomer(f.customerId()),
                hasStatus(f.status()),
                issuedBetween(f.issuedFrom(), f.issuedTo()));
    }

    /** Filtro de las estadísticas del resumen (spec 12): las mismas condiciones que el listado. */
    public static Specification<Invoice> forStats(InvoiceStatsFilter filter) {
        InvoiceStatsFilter f = filter == null ? InvoiceStatsFilter.empty() : filter;
        return Specification.allOf(hasCustomer(f.customerId()), issuedBetween(f.issuedFrom(), f.issuedTo()));
    }
}

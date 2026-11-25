package com.andres.course.claude.springboot.springapi.app.repositories;

import com.andres.course.claude.springboot.springapi.app.models.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    // Listado: trae el cliente en la misma consulta (las líneas llegan por @BatchSize).
    @Override
    @EntityGraph(attributePaths = "customer")
    Page<Invoice> findAll(Specification<Invoice> spec, Pageable pageable);

    // Detalle: la factura con su cliente y sus líneas en una sola consulta.
    @EntityGraph(attributePaths = {"customer", "items"})
    Optional<Invoice> findWithCustomerAndItemsById(Long id);

    long countByCustomerId(Long customerId);

    // Última factura con ese prefijo (p. ej. "FAC-2026-"), para calcular el siguiente correlativo.
    Optional<Invoice> findTopByInvoiceNumberStartingWithOrderByInvoiceNumberDesc(String prefix);
}

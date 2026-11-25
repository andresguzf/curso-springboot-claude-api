package com.andres.course.claude.springboot.springapi.app.repositories;

import com.andres.course.claude.springboot.springapi.app.models.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    // Listado: trae el cliente en la misma consulta (las líneas llegan por @BatchSize).
    @Override
    @EntityGraph(attributePaths = "customer")
    Page<Invoice> findAll(Specification<Invoice> spec, Pageable pageable);

    // Detalle: la factura con su cliente y sus líneas en una sola consulta.
    @EntityGraph(attributePaths = {"customer", "items"})
    Optional<Invoice> findWithCustomerAndItemsById(Long id);

    // Último número con ese prefijo (p. ej. "FAC-2026-"), para calcular el siguiente correlativo.
    // Nativa a propósito: así NO aplica el @SQLRestriction y cuenta también las facturas eliminadas
    // (borrado lógico). Si no, se reutilizaría su número y el UNIQUE de la base lo rechazaría.
    @Query(value = "select max(invoice_number) from invoices where invoice_number like :prefix || '%'", nativeQuery = true)
    Optional<String> findLastNumberWithPrefix(@Param("prefix") String prefix);

    // Borrado lógico de las facturas de un cliente (spec 11). Devuelve cuántas se marcaron.
    @Modifying
    @Query("update Invoice i set i.deletedAt = :deletedAt where i.customer.id = :customerId and i.deletedAt is null")
    int softDeleteByCustomerId(@Param("customerId") Long customerId, @Param("deletedAt") LocalDateTime deletedAt);
}

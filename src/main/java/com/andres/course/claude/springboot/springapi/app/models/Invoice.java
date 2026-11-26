package com.andres.course.claude.springboot.springapi.app.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Factura de un cliente. La tabla ya existe en Supabase (la creó una migración, no Hibernate):
 * los nombres de columnas, restricciones e índices copian ese esquema para que el perfil prod
 * (ddl-auto=validate) lo acepte y el de desarrollo (update) no cree duplicados.
 *
 * Los importes (subtotal, tax, total) los calcula el service a partir de las líneas.
 *
 * Borrado lógico (spec 11): al eliminar su cliente, la factura recibe deleted_at y @SQLRestriction la
 * excluye de toda consulta de Hibernate. Eliminar una factura sola sigue siendo físico (por eso no se
 * usa @SoftDelete, que volvería lógico todo delete).
 */
@Entity
@Table(name = "invoices",
        uniqueConstraints = @UniqueConstraint(name = "invoices_invoice_number_key", columnNames = "invoice_number"),
        indexes = @Index(name = "idx_invoices_customer_id", columnList = "customer_id"))
@Check(name = "chk_invoices_due_date", constraints = "due_date IS NULL OR due_date >= issue_date")
@Check(name = "invoices_subtotal_check", constraints = "subtotal >= 0")
@Check(name = "invoices_tax_check", constraints = "tax >= 0")
@Check(name = "invoices_total_check", constraints = "total >= 0")
@SQLRestriction("deleted_at IS NULL")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FAC-<año>-<NNNN>; lo asigna el service y no cambia al editar.
    @Column(name = "invoice_number", nullable = false, length = 30)
    private String invoiceNumber;

    // Sin ON DELETE: un cliente con facturas no se puede eliminar (lo controla CustomerServiceImpl).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "invoices_customer_id_fkey"))
    private Customer customer;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    // IVA del subtotal.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.PENDING;

    private String notes;

    // Líneas ordenadas por número. Al guardar la factura se guardan sus líneas (cascade) y una línea
    // quitada de la lista se borra (orphanRemoval). @BatchSize carga las líneas de varias facturas
    // en una sola consulta (listado sin N+1).
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber")
    @BatchSize(size = 50)
    private List<InvoiceItem> items = new ArrayList<>();

    // Auditoría: fechas manejadas por el sistema en los eventos del ciclo de vida.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Borrado lógico (con su cliente): null mientras no se haya eliminado.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public Invoice() {
    }

    // Se ejecuta justo antes del INSERT: registra la fecha de creación.
    @PrePersist
    protected void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // Se ejecuta justo antes del UPDATE: registra la fecha de la última edición.
    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** Agrega una línea y mantiene las dos puntas de la relación. */
    public void addItem(InvoiceItem item) {
        items.add(item);
        item.setInvoice(this);
    }

    /** Quita una línea (orphanRemoval la borra al guardar) y mantiene las dos puntas de la relación. */
    public void removeItem(InvoiceItem item) {
        items.remove(item);
        item.setInvoice(null);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<InvoiceItem> getItems() {
        return items;
    }

    public void setItems(List<InvoiceItem> items) {
        this.items = items;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}

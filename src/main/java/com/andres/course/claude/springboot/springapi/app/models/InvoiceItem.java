package com.andres.course.claude.springboot.springapi.app.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Línea de detalle de una factura. Como invoices, la tabla ya existe en Supabase y este mapeo
 * copia su esquema (columna generada, restricción única por número de línea e índice).
 */
@Entity
@Table(name = "invoice_items",
        uniqueConstraints = @UniqueConstraint(name = "uq_invoice_items_line", columnNames = {"invoice_id", "line_number"}),
        indexes = @Index(name = "idx_invoice_items_invoice_id", columnList = "invoice_id"))
@Check(name = "invoice_items_line_number_check", constraints = "line_number > 0")
@Check(name = "invoice_items_quantity_check", constraints = "quantity > 0")
@Check(name = "invoice_items_unit_price_check", constraints = "unit_price >= 0")
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Al borrar la factura, la base de datos borra sus líneas (ON DELETE CASCADE).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, foreignKey = @ForeignKey(name = "invoice_items_invoice_id_fkey"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Invoice invoice;

    // 1..n dentro de la factura; único junto con invoice_id.
    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    // Columna generada por PostgreSQL: Hibernate nunca la escribe (insertable/updatable = false) y la
    // relee después de cada INSERT o UPDATE (@Generated). La definición completa lleva STORED, como
    // en Supabase: @GeneratedColumn no lo agrega y PostgreSQL 18 crearía la columna VIRTUAL (y
    // PostgreSQL 17 rechaza la sintaxis sin STORED). Los importes de la factura se calculan en Java
    // con calculateLineTotal(), que usa la misma fórmula.
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    // precision y scale se repiten aunque estén en la definición: ddl-auto=update las compara con las
    // de la base y, si faltan, intenta un ALTER COLUMN en cada arranque.
    @Column(name = "line_total", insertable = false, updatable = false, precision = 12, scale = 2,
            columnDefinition = "numeric(12,2) generated always as (round(quantity * unit_price, 2)) stored")
    private BigDecimal lineTotal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public InvoiceItem() {
    }

    @PrePersist
    protected void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    /** cantidad × precio unitario redondeado a 2 decimales, igual que round() de PostgreSQL. */
    public BigDecimal calculateLineTotal() {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

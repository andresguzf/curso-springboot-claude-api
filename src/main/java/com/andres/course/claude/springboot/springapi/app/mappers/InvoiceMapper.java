package com.andres.course.claude.springboot.springapi.app.mappers;

import com.andres.course.claude.springboot.springapi.app.dto.InvoiceDto;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceItemDto;
import com.andres.course.claude.springboot.springapi.app.models.Customer;
import com.andres.course.claude.springboot.springapi.app.models.Invoice;
import com.andres.course.claude.springboot.springapi.app.models.InvoiceItem;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Mapeo entity <-> dto de facturas. El cliente, las líneas, el número y los importes de una factura
 * nueva o editada los resuelve el service (necesitan repositorios y reglas de negocio); aquí solo se
 * copian los campos editables.
 */
@Component
public class InvoiceMapper {

    /** entity -> dto (con el nombre del cliente y las líneas en su orden). */
    public InvoiceDto toDto(Invoice entity) {
        if (entity == null) {
            return null;
        }
        Customer customer = entity.getCustomer();
        return new InvoiceDto(
                entity.getId(),
                entity.getInvoiceNumber(),
                customer.getId(),
                customer.getName() + " " + customer.getLastName(),
                entity.getIssueDate(),
                entity.getDueDate(),
                entity.getStatus(),
                entity.getNotes(),
                entity.getItems().stream().map(this::toDto).toList(),
                entity.getSubtotal(),
                entity.getTax(),
                entity.getTotal(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /** Línea entity -> dto. El total se calcula con la misma fórmula que la columna generada. */
    public InvoiceItemDto toDto(InvoiceItem entity) {
        return new InvoiceItemDto(
                entity.getLineNumber(),
                entity.getDescription(),
                entity.getQuantity(),
                entity.getUnitPrice(),
                entity.calculateLineTotal()
        );
    }

    /** dto -> entity nueva, solo con los campos editables (sin cliente, líneas, número ni importes). */
    public Invoice toEntity(InvoiceDto dto) {
        if (dto == null) {
            return null;
        }
        Invoice entity = new Invoice();
        updateEntity(dto, entity);
        return entity;
    }

    /**
     * Copia los campos editables del dto sobre una factura existente. Nunca el número, los importes
     * ni las fechas de auditoría. Sin estado en el dto, se conserva el actual (PENDING en una nueva).
     */
    public void updateEntity(InvoiceDto dto, Invoice entity) {
        entity.setIssueDate(dto.issueDate());
        entity.setDueDate(dto.dueDate());
        if (dto.status() != null) {
            entity.setStatus(dto.status());
        }
        // Notas vacías o solo con espacios se guardan como null.
        entity.setNotes(StringUtils.hasText(dto.notes()) ? dto.notes().trim() : null);
    }

    /** Copia los campos editables de una línea (nunca el número de línea, que asigna el service). */
    public void updateItem(InvoiceItemDto dto, InvoiceItem entity) {
        entity.setDescription(dto.description().trim());
        entity.setQuantity(dto.quantity());
        entity.setUnitPrice(dto.unitPrice());
    }
}

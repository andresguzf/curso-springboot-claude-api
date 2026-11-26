package com.andres.course.claude.springboot.springapi.app.dto;

import com.andres.course.claude.springboot.springapi.app.models.InvoiceStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO único de Invoice para request y response.
 *
 * El cliente de la API envía el cliente, las fechas, el estado, las notas y las líneas. Son de solo
 * lectura (salen en la respuesta y se ignoran en el request) el número de factura, el nombre del
 * cliente, los importes y las fechas de auditoría: los calcula o asigna el sistema.
 */
public record InvoiceDto(
        Long id,

        // Solo lectura: FAC-<año de emisión>-<NNNN>, lo asigna el sistema al crear.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        String invoiceNumber,

        @NotNull(message = "El cliente es obligatorio")
        Long customerId,

        // Solo lectura: nombre y apellido del cliente, para mostrarlo sin otra consulta.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        String customerName,

        @NotNull(message = "La fecha de emisión es obligatoria")
        LocalDate issueDate,

        // Opcional; no puede ser anterior a la emisión (lo valida el service).
        LocalDate dueDate,

        // Opcional: si no se envía al crear, la factura queda PENDING; al editar, conserva el actual.
        InvoiceStatus status,

        @Size(max = 255, message = "Las notas no pueden superar los {max} caracteres")
        String notes,

        // @Valid en el tipo valida cada línea (sobre la lista está deprecado en Hibernate Validator 9);
        // sus errores llegan como items[0].description, items[1].quantity...
        @NotEmpty(message = "La factura debe tener al menos una línea")
        @Size(max = 100, message = "La factura no puede tener más de {max} líneas")
        List<@Valid InvoiceItemDto> items,

        // Solo lectura: suma de las líneas.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        BigDecimal subtotal,

        // Solo lectura: IVA (19 %) del subtotal.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        BigDecimal tax,

        // Solo lectura: subtotal + IVA.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        BigDecimal total,

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        LocalDateTime createdAt,

        // Solo lectura: null mientras la factura no se haya modificado nunca.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        LocalDateTime updatedAt
) {
}

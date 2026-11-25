package com.andres.course.claude.springboot.springapi.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO de una línea de factura, para request y response. Viaja siempre dentro de {@link InvoiceDto}.
 * El número de línea y el total de la línea son de solo lectura: los asigna el sistema.
 * Los límites de cantidad y precio son los de las columnas numeric(10,2) y numeric(12,2).
 */
public record InvoiceItemDto(
        // Solo lectura: lo asigna el sistema (1..n en el orden enviado al crear; ver InvoiceServiceImpl).
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        Integer lineNumber,

        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 255, message = "La descripción no puede superar los {max} caracteres")
        String description,

        @NotNull(message = "La cantidad es obligatoria")
        @DecimalMin(value = "0", inclusive = false, message = "La cantidad debe ser mayor que 0")
        @Digits(integer = 8, fraction = 2, message = "La cantidad admite hasta {integer} enteros y {fraction} decimales")
        BigDecimal quantity,

        @NotNull(message = "El precio unitario es obligatorio")
        @DecimalMin(value = "0", message = "El precio unitario no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El precio unitario admite hasta {integer} enteros y {fraction} decimales")
        BigDecimal unitPrice,

        // Solo lectura: cantidad × precio unitario, redondeado a 2 decimales.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        BigDecimal lineTotal
) {
}

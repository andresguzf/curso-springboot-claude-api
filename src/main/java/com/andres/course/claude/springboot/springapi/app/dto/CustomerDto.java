package com.andres.course.claude.springboot.springapi.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * DTO único de Customer para request y response.
 * No incluye las fechas de auditoría (createdAt / updatedAt): las maneja el sistema.
 */
public record CustomerDto(
        Long id,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80, message = "El nombre no puede superar los {max} caracteres")
        String name,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 80, message = "El apellido no puede superar los {max} caracteres")
        String lastName,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no puede superar los {max} caracteres")
        String email,

        @Size(max = 30, message = "El teléfono no puede superar los {max} caracteres")
        String phone,

        @Size(max = 200, message = "La dirección no puede superar los {max} caracteres")
        String address,

        @Size(max = 80, message = "La ciudad no puede superar los {max} caracteres")
        String city,

        @Past(message = "La fecha de nacimiento debe ser una fecha pasada")
        LocalDate birthDate,

        // Opcional: si no se envía al crear, el cliente queda activo.
        Boolean active
) {
}

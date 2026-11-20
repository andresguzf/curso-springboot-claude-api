package com.andres.course.claude.springboot.springapi.app.dto;

import com.andres.course.claude.springboot.springapi.app.models.Role;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * DTO único de User para request y response.
 * El password es solo de escritura: se acepta en el request pero nunca sale en la respuesta.
 * Las fechas de auditoría (createdAt / updatedAt) son de solo lectura: salen en la
 * respuesta, pero se ignoran en el request porque las asigna el sistema.
 */
public record UserDto(
        Long id,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no puede superar los {max} caracteres")
        String email,

        // Obligatorio al crear (lo exige el service); al editar es opcional: si no viene, se conserva.
        // Máximo 72: BCrypt ignora lo que pase de 72 bytes.
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre {min} y {max} caracteres")
        String password,

        @NotNull(message = "El rol es obligatorio")
        Role role,

        // Opcional: si no se envía al crear, el usuario queda habilitado.
        Boolean enabled,

        // Solo lectura: las asigna el sistema (@PrePersist / @PreUpdate) y se ignoran en el request.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        LocalDateTime createdAt,

        // Solo lectura: null mientras el usuario no se haya modificado nunca.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        LocalDateTime updatedAt
) {
}

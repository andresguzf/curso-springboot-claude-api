package com.andres.course.claude.springboot.springapi.app.dto;

import com.andres.course.claude.springboot.springapi.app.models.Role;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO único de User para request y response.
 * El password es solo de escritura: se acepta en el request pero nunca sale en la respuesta.
 * No incluye las fechas de auditoría (createdAt / updatedAt): las maneja el sistema.
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
        Boolean enabled
) {
}

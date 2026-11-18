package com.andres.course.claude.springboot.springapi.app.dto;

import jakarta.validation.constraints.NotBlank;

/** Credenciales para POST /api/auth/login. */
public record LoginDto(
        @NotBlank(message = "El email es obligatorio")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password
) {
}

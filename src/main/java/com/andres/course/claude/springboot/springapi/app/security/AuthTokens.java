package com.andres.course.claude.springboot.springapi.app.security;

import com.andres.course.claude.springboot.springapi.app.dto.UserDto;

/**
 * Resultado interno de login y refresh: los tokens se escriben en cookies HttpOnly
 * y en el cuerpo de la respuesta solo sale el usuario.
 */
public record AuthTokens(String accessToken, String refreshToken, UserDto user) {
}

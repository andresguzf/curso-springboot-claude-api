package com.andres.course.claude.springboot.springapi.app.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuración de los tokens (prefijo app.jwt en application.properties).
 *
 * @param secret                 clave HMAC en Base64 (mínimo 256 bits), desde la variable JWT_SECRET
 * @param issuer                 emisor que se escribe y se exige en el claim iss
 * @param accessTokenExpiration  vida del access token (JWT), p. ej. 15m
 * @param refreshTokenExpiration vida del refresh token, p. ej. 7d
 * @param cookieSecure           atributo Secure de las cookies (true en producción con HTTPS)
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenExpiration,
        @NotNull Duration refreshTokenExpiration,
        boolean cookieSecure
) {
}

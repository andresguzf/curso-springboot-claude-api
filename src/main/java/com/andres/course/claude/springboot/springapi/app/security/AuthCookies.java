package com.andres.course.claude.springboot.springapi.app.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Arma las cookies de los tokens. Ambas son:
 * - HttpOnly: el JavaScript del navegador no puede leerlas (mitiga el robo de tokens por XSS).
 * - SameSite=Strict: el navegador no las envía en peticiones que vienen de otro sitio (mitiga CSRF).
 * - Secure (configurable): solo viajan por HTTPS.
 *
 * El refresh token usa Path=/api/auth para que solo viaje a los endpoints de autenticación.
 */
@Component
public class AuthCookies {

    public static final String ACCESS_TOKEN = "access_token";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String REFRESH_TOKEN_PATH = "/api/auth";

    private final JwtProperties properties;

    public AuthCookies(JwtProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie accessToken(String token) {
        return build(ACCESS_TOKEN, token, "/", properties.accessTokenExpiration());
    }

    public ResponseCookie refreshToken(String token) {
        return build(REFRESH_TOKEN, token, REFRESH_TOKEN_PATH, properties.refreshTokenExpiration());
    }

    /** Cookie vacía con Max-Age=0: el navegador borra la cookie del access token. */
    public ResponseCookie clearAccessToken() {
        return build(ACCESS_TOKEN, "", "/", Duration.ZERO);
    }

    /** Cookie vacía con Max-Age=0: el navegador borra la cookie del refresh token. */
    public ResponseCookie clearRefreshToken() {
        return build(REFRESH_TOKEN, "", REFRESH_TOKEN_PATH, Duration.ZERO);
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}

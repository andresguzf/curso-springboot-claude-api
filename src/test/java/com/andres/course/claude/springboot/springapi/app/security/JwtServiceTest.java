package com.andres.course.claude.springboot.springapi.app.security;

import com.andres.course.claude.springboot.springapi.app.models.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("clave-de-prueba-de-al-menos-32-bytes!!".getBytes());
    private static final String OTHER_SECRET = Base64.getEncoder()
            .encodeToString("otra-clave-de-prueba-de-32-bytes-o-mas".getBytes());

    private static JwtService service(String secret, String issuer, Duration expiration) {
        return new JwtService(new JwtProperties(secret, issuer, expiration, Duration.ofDays(7), true));
    }

    @Test
    void generaYValidaElToken() {
        JwtService jwtService = service(SECRET, "3-spring-api", Duration.ofMinutes(15));

        Claims claims = jwtService.parse(jwtService.generateAccessToken("ana@ejemplo.com", Role.ADMIN));

        assertThat(claims.getSubject()).isEqualTo("ana@ejemplo.com");
        assertThat(claims.get(JwtService.ROLE_CLAIM, String.class)).isEqualTo("ADMIN");
        assertThat(claims.getIssuer()).isEqualTo("3-spring-api");
    }

    @Test
    void rechazaTokenExpirado() {
        JwtService jwtService = service(SECRET, "3-spring-api", Duration.ofSeconds(-1));
        String token = jwtService.generateAccessToken("ana@ejemplo.com", Role.USER);

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rechazaTokenFirmadoConOtraClave() {
        String token = service(OTHER_SECRET, "3-spring-api", Duration.ofMinutes(15))
                .generateAccessToken("ana@ejemplo.com", Role.ADMIN);

        assertThatThrownBy(() -> service(SECRET, "3-spring-api", Duration.ofMinutes(15)).parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaTokenDeOtroEmisor() {
        String token = service(SECRET, "otra-app", Duration.ofMinutes(15))
                .generateAccessToken("ana@ejemplo.com", Role.ADMIN);

        assertThatThrownBy(() -> service(SECRET, "3-spring-api", Duration.ofMinutes(15)).parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void noAceptaClavesDebiles() {
        String weak = Base64.getEncoder().encodeToString("corta".getBytes());

        assertThatThrownBy(() -> service(weak, "3-spring-api", Duration.ofMinutes(15)))
                .isInstanceOf(WeakKeyException.class);
    }
}

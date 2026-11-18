package com.andres.course.claude.springboot.springapi.app.security;

import com.andres.course.claude.springboot.springapi.app.models.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * Genera y valida el access token (JWT firmado con HMAC-SHA256).
 * El token lleva el email en "sub" y el rol en el claim "role"; viaja en una cookie HttpOnly.
 */
@Component
public class JwtService {

    public static final String ROLE_CLAIM = "role";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        // Lanza WeakKeyException si la clave tiene menos de 256 bits: la app no arranca con una clave débil.
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }

    public String generateAccessToken(String email, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .issuer(properties.issuer())
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenExpiration())))
                .signWith(key)
                .compact();
    }

    /**
     * Valida firma, emisor y expiración y devuelve los claims.
     *
     * @throws JwtException si el token es inválido o expiró (ExpiredJwtException)
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

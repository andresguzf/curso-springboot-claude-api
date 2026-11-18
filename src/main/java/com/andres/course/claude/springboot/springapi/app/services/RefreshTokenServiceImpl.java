package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.exceptions.InvalidTokenException;
import com.andres.course.claude.springboot.springapi.app.models.RefreshToken;
import com.andres.course.claude.springboot.springapi.app.models.User;
import com.andres.course.claude.springboot.springapi.app.repositories.RefreshTokenRepository;
import com.andres.course.claude.springboot.springapi.app.security.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);
    private static final int TOKEN_BYTES = 32; // 256 bits

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenServiceImpl(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    @Override
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setTokenHash(hash(rawToken));
        token.setUser(user);
        token.setExpiresAt(Instant.now().plus(jwtProperties.refreshTokenExpiration()));
        refreshTokenRepository.save(token);
        return rawToken;
    }

    // noRollbackFor: si se detecta reutilización, la revocación de todos los tokens debe quedar
    // guardada aunque el método termine lanzando InvalidTokenException.
    @Override
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public Rotation rotate(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidTokenException("El refresh token no es válido"));
        User user = token.getUser();

        if (token.isRevoked()) {
            // Un token ya usado vuelve a aparecer: alguien pudo robarlo. Se cierran todas las sesiones del usuario.
            log.warn("Reutilización de refresh token detectada para el usuario id={}", user.getId());
            refreshTokenRepository.revokeAllByUser(user);
            throw new InvalidTokenException("El refresh token ya fue usado: inicia sesión de nuevo");
        }
        if (token.isExpired()) {
            throw new InvalidTokenException("El refresh token expiró: inicia sesión de nuevo");
        }
        if (!user.isEnabled()) {
            throw new InvalidTokenException("El usuario está deshabilitado");
        }

        token.setRevoked(true);
        return new Rotation(user, issue(user));
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> token.setRevoked(true));
    }

    @Override
    @Transactional
    public void revokeAll(User user) {
        refreshTokenRepository.revokeAllByUser(user);
    }

    // Todos los días a las 03:00 borra los tokens vencidos.
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpired(Instant.now());
        log.info("Refresh tokens vencidos eliminados: {}", deleted);
    }

    // SHA-256 en hexadecimal (64 caracteres). Basta un hash rápido: el token es aleatorio de 256 bits,
    // no una contraseña que se pueda adivinar por diccionario.
    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}

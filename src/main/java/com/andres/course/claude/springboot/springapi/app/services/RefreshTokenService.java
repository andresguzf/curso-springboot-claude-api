package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.models.User;

/**
 * Refresh tokens opacos guardados (hasheados) en la tabla refresh_tokens, con rotación en cada uso.
 * Es un service interno: lo usan AuthService y UserService, no los controllers.
 */
public interface RefreshTokenService {

    /** Crea un refresh token para el usuario y devuelve su valor en claro (solo se guarda el hash). */
    String issue(User user);

    /**
     * Valida el token, lo revoca y emite uno nuevo (rotación).
     * Si el token ya estaba revocado (reutilización), revoca todos los del usuario.
     *
     * @throws com.andres.course.claude.springboot.springapi.app.exceptions.InvalidTokenException si el token no es válido
     */
    Rotation rotate(String rawToken);

    /** Revoca el token si existe (logout). */
    void revoke(String rawToken);

    /** Revoca todos los tokens vigentes del usuario. */
    void revokeAll(User user);

    /** Usuario dueño del token rotado y el nuevo refresh token en claro. */
    record Rotation(User user, String refreshToken) {
    }
}

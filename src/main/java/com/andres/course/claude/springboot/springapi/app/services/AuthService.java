package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.LoginDto;
import com.andres.course.claude.springboot.springapi.app.security.AuthTokens;

public interface AuthService {

    /** Valida las credenciales y emite access + refresh token. */
    AuthTokens login(LoginDto dto);

    /** Rota el refresh token y emite un access token nuevo. */
    AuthTokens refresh(String refreshToken);

    /** Revoca el refresh token (si viene). */
    void logout(String refreshToken);
}

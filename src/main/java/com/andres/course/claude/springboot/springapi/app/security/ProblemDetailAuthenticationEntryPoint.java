package com.andres.course.claude.springboot.springapi.app.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 401: petición sin autenticar (sin cookie, token inválido o expirado) a una ruta protegida. */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailResponseWriter writer;

    public ProblemDetailAuthenticationEntryPoint(ProblemDetailResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object jwtError = request.getAttribute(JwtAuthenticationFilter.JWT_ERROR_ATTRIBUTE);
        String detail;
        if (JwtAuthenticationFilter.JWT_EXPIRED.equals(jwtError)) {
            detail = "La sesión expiró: renueva el token con POST /api/auth/refresh";
        } else if (JwtAuthenticationFilter.JWT_INVALID.equals(jwtError)) {
            detail = "El token de acceso no es válido: inicia sesión de nuevo";
        } else {
            detail = "Debes iniciar sesión para acceder a este recurso";
        }
        writer.write(request, response, HttpStatus.UNAUTHORIZED, "No autenticado", detail);
    }
}

package com.andres.course.claude.springboot.springapi.app.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 403: falta el token CSRF, o el usuario está autenticado pero su rol no tiene permiso. */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailResponseWriter writer;

    public ProblemDetailAccessDeniedHandler(ProblemDetailResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        if (ex instanceof CsrfException) {
            writer.write(request, response, HttpStatus.FORBIDDEN, "Token CSRF inválido",
                    "Falta el token CSRF o no es válido: envía la cabecera X-XSRF-TOKEN con el valor de la cookie XSRF-TOKEN");
            return;
        }
        writer.write(request, response, HttpStatus.FORBIDDEN, "Acceso denegado",
                "No tienes permisos para realizar esta operación");
    }
}

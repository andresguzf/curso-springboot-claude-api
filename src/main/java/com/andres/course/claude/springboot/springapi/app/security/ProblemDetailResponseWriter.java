package com.andres.course.claude.springboot.springapi.app.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Escribe un ProblemDetail como JSON desde los filtros de seguridad. Los 401/403 ocurren
 * antes de llegar a los controllers, así que GlobalExceptionHandler no los ve.
 */
@Component
public class ProblemDetailResponseWriter {

    private final JsonMapper jsonMapper;

    public ProblemDetailResponseWriter(JsonMapper jsonMapper) {
        // El mixin de Spring da el mismo formato que Spring MVC (sin nulos y con las propiedades extra al primer nivel).
        this.jsonMapper = jsonMapper.rebuild()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                .build();
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}

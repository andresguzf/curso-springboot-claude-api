package com.andres.course.claude.springboot.springapi.app.exceptions;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce las excepciones a respuestas JSON con formato ProblemDetail (RFC 9457).
 *
 * Extiende ResponseEntityExceptionHandler para reemplazar el manejador por defecto de
 * Spring Boot (spring.mvc.problemdetails.enabled): así el error de validación usa
 * nuestro formato con los mensajes por campo, y el resto de errores de Spring MVC
 * (JSON mal formado, método no permitido, etc.) sigue respondiendo como ProblemDetail.
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // 404: el recurso (p. ej. un cliente) no existe.
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Recurso no encontrado");
        return problem;
    }

    // 409: el email (u otro valor único) ya está en uso, detectado por el service.
    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Recurso duplicado");
        return problem;
    }

    // 409: la base de datos rechazó el cambio por una restricción (p. ej. el UNIQUE del email
    // cuando dos peticiones simultáneas pasan la validación del service a la vez).
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "El dato ya está en uso o viola una restricción de la base de datos");
        problem.setTitle("Conflicto de datos");
        return problem;
    }

    // 401: login fallido. BadCredentialsException, DisabledException, etc. responden con el mismo
    // mensaje genérico para no revelar si el email existe o si la cuenta está deshabilitada.
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Email o contraseña incorrectos");
        problem.setTitle("No autenticado");
        return problem;
    }

    // 401: refresh token ausente, vencido, revocado o reutilizado.
    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Token inválido");
        return problem;
    }

    // 409: la operación viola una regla de negocio (p. ej. un admin que se borra a sí mismo).
    @ExceptionHandler(OperationNotAllowedException.class)
    public ProblemDetail handleOperationNotAllowed(OperationNotAllowedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Operación no permitida");
        return problem;
    }

    // 400: dato inválido detectado por el service (p. ej. crear un usuario sin contraseña).
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Solicitud inválida");
        return problem;
    }

    // 400: campos del DTO que no pasan Bean Validation, con su mensaje por campo.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Hay campos con errores de validación");
        problem.setTitle("Error de validación");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }
}

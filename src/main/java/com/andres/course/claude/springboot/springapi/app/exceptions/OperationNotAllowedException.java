package com.andres.course.claude.springboot.springapi.app.exceptions;

/** Operación válida en forma pero prohibida por una regla de negocio (p. ej. un admin que se borra a sí mismo). Se traduce a 409. */
public class OperationNotAllowedException extends RuntimeException {

    public OperationNotAllowedException(String message) {
        super(message);
    }
}

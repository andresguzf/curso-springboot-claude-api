package com.andres.course.claude.springboot.springapi.app.exceptions;

/** Refresh token ausente, desconocido, vencido, revocado o reutilizado. Se traduce a 401. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}

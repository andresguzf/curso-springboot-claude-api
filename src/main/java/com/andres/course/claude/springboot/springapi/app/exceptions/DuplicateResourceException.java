package com.andres.course.claude.springboot.springapi.app.exceptions;

public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String resource, String field, Object value) {
        super(resource + " con " + field + " '" + value + "' ya existe");
    }
}

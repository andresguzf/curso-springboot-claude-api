package com.andres.course.claude.springboot.springapi.app.exceptions;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " con id " + id + " no encontrado");
    }

    public ResourceNotFoundException(String resource, String field, Object value) {
        super(resource + " con " + field + " '" + value + "' no encontrado");
    }
}

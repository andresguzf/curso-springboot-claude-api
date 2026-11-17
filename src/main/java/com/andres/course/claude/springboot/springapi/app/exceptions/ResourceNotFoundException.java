package com.andres.course.claude.springboot.springapi.app.exceptions;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " con id " + id + " no encontrado");
    }
}

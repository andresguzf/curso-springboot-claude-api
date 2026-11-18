package com.andres.course.claude.springboot.springapi.app.models;

/**
 * Roles de la aplicación. Spring Security los usa como autoridades con el prefijo ROLE_
 * (ROLE_USER, ROLE_ADMIN).
 */
public enum Role {
    /** Solo puede ver y buscar clientes. */
    USER,
    /** Administra clientes y usuarios. */
    ADMIN
}

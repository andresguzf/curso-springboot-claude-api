package com.andres.course.claude.springboot.springapi.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Administrador inicial (prefijo app.admin), desde las variables ADMIN_EMAIL y ADMIN_PASSWORD. */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String email, String password) {
}

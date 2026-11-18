package com.andres.course.claude.springboot.springapi.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita las tareas @Scheduled (p. ej. la limpieza de refresh tokens vencidos). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

package com.andres.course.claude.springboot.springapi.app.config;

import com.andres.course.claude.springboot.springapi.app.dto.UserDto;
import com.andres.course.claude.springboot.springapi.app.models.Role;
import com.andres.course.claude.springboot.springapi.app.repositories.UserRepository;
import com.andres.course.claude.springboot.springapi.app.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Crea el administrador inicial al arrancar (ADMIN_EMAIL / ADMIN_PASSWORD), si aún no existe.
 * No va en data.sql para no versionar contraseñas ni hashes.
 */
@Component
public class AdminUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final UserService userService;

    public AdminUserInitializer(AdminProperties adminProperties, UserRepository userRepository, UserService userService) {
        this.adminProperties = adminProperties;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(adminProperties.email()) || userRepository.existsByEmail(adminProperties.email())) {
            return;
        }
        if (!StringUtils.hasText(adminProperties.password())) {
            log.warn("No se creó el administrador inicial: define ADMIN_PASSWORD en el .env");
            return;
        }
        userService.create(new UserDto(null, adminProperties.email(), adminProperties.password(), Role.ADMIN, true));
        log.info("Administrador inicial creado");
    }
}

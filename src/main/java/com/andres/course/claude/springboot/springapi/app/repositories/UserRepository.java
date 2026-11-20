package com.andres.course.claude.springboot.springapi.app.repositories;

import com.andres.course.claude.springboot.springapi.app.models.Role;
import com.andres.course.claude.springboot.springapi.app.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

// JpaSpecificationExecutor: findAll(Specification, Pageable) para el listado con filtros (UserSpecifications).
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Cuántos usuarios habilitados tienen ese rol (para no dejar el sistema sin administradores). */
    long countByRoleAndEnabledTrue(Role role);
}

package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.UserDto;
import com.andres.course.claude.springboot.springapi.app.dto.UserFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    /**
     * Página de usuarios que cumplen el filtro (búsqueda por email, rol y estado).
     *
     * @throws IllegalArgumentException si se pide ordenar por un campo no permitido (400)
     */
    Page<UserDto> findPage(UserFilter filter, Pageable pageable);

    UserDto findById(Long id);

    UserDto findByEmail(String email);

    UserDto create(UserDto dto);

    /** @param currentUserEmail email del admin que hace el cambio (no puede quitarse permisos a sí mismo) */
    UserDto update(Long id, UserDto dto, String currentUserEmail);

    /** @param currentUserEmail email del admin que borra (no puede borrarse a sí mismo) */
    void delete(Long id, String currentUserEmail);
}

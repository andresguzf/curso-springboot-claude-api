package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.UserDto;

import java.util.List;

public interface UserService {

    List<UserDto> findAll();

    UserDto findById(Long id);

    UserDto findByEmail(String email);

    UserDto create(UserDto dto);

    /** @param currentUserEmail email del admin que hace el cambio (no puede quitarse permisos a sí mismo) */
    UserDto update(Long id, UserDto dto, String currentUserEmail);

    /** @param currentUserEmail email del admin que borra (no puede borrarse a sí mismo) */
    void delete(Long id, String currentUserEmail);
}

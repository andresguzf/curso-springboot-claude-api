package com.andres.course.claude.springboot.springapi.app.mappers;

import com.andres.course.claude.springboot.springapi.app.dto.UserDto;
import com.andres.course.claude.springboot.springapi.app.models.User;
import org.springframework.stereotype.Component;

/** El password nunca se copia en ninguna dirección: lo hashea y asigna el service. */
@Component
public class UserMapper {

    /** entity -> dto (sin password) */
    public UserDto toDto(User entity) {
        if (entity == null) {
            return null;
        }
        return new UserDto(entity.getId(), entity.getEmail(), null, entity.getRole(), entity.isEnabled());
    }

    /** dto -> entity (nueva; el id, el password y las fechas los asigna el sistema) */
    public User toEntity(UserDto dto) {
        if (dto == null) {
            return null;
        }
        User entity = new User();
        updateEntity(dto, entity);
        return entity;
    }

    /** Copia los campos editables del dto sobre una entidad existente. */
    public void updateEntity(UserDto dto, User entity) {
        entity.setEmail(dto.email());
        entity.setRole(dto.role());
        if (dto.enabled() != null) {
            entity.setEnabled(dto.enabled());
        }
    }
}

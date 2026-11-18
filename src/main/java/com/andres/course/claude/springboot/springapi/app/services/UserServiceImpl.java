package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.UserDto;
import com.andres.course.claude.springboot.springapi.app.exceptions.DuplicateResourceException;
import com.andres.course.claude.springboot.springapi.app.exceptions.OperationNotAllowedException;
import com.andres.course.claude.springboot.springapi.app.exceptions.ResourceNotFoundException;
import com.andres.course.claude.springboot.springapi.app.mappers.UserMapper;
import com.andres.course.claude.springboot.springapi.app.models.Role;
import com.andres.course.claude.springboot.springapi.app.models.User;
import com.andres.course.claude.springboot.springapi.app.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper,
                           PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        return userRepository.findAll()
                .stream()
                .map(userMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto findById(Long id) {
        return userMapper.toDto(getOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto findByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(userMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Override
    @Transactional
    public UserDto create(UserDto dto) {
        if (dto.password() == null || dto.password().isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria al crear un usuario");
        }
        if (userRepository.existsByEmail(dto.email())) {
            throw new DuplicateResourceException("User", "email", dto.email());
        }
        User entity = userMapper.toEntity(dto);
        entity.setPassword(passwordEncoder.encode(dto.password()));
        return userMapper.toDto(userRepository.save(entity));
    }

    @Override
    @Transactional
    public UserDto update(Long id, UserDto dto, String currentUserEmail) {
        User entity = getOrThrow(id);
        userRepository.findByEmail(dto.email())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("User", "email", dto.email());
                });

        boolean editingSelf = entity.getEmail().equals(currentUserEmail);
        boolean disabling = dto.enabled() != null && !dto.enabled();
        if (editingSelf && (dto.role() != Role.ADMIN || disabling)) {
            throw new OperationNotAllowedException("No puedes quitarte el rol ADMIN ni deshabilitar tu propio usuario");
        }

        boolean roleChanged = entity.getRole() != dto.role();
        boolean passwordChanged = dto.password() != null && !dto.password().isBlank();

        userMapper.updateEntity(dto, entity);
        if (passwordChanged) {
            entity.setPassword(passwordEncoder.encode(dto.password()));
        }
        // Al cambiar permisos o credenciales se cierran sus sesiones: no podrá renovar el token y
        // el cambio aplica, como máximo, cuando venza su access token actual.
        if (roleChanged || passwordChanged || !entity.isEnabled()) {
            refreshTokenService.revokeAll(entity);
        }
        return userMapper.toDto(userRepository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id, String currentUserEmail) {
        User entity = getOrThrow(id);
        if (entity.getEmail().equals(currentUserEmail)) {
            throw new OperationNotAllowedException("No puedes borrar tu propio usuario");
        }
        // Sus refresh tokens se borran en la base de datos por ON DELETE CASCADE.
        userRepository.delete(entity);
    }

    private User getOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}

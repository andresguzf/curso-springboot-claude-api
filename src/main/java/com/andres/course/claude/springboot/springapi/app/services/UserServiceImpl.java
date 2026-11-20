package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.UserDto;
import com.andres.course.claude.springboot.springapi.app.dto.UserFilter;
import com.andres.course.claude.springboot.springapi.app.exceptions.DuplicateResourceException;
import com.andres.course.claude.springboot.springapi.app.exceptions.OperationNotAllowedException;
import com.andres.course.claude.springboot.springapi.app.exceptions.ResourceNotFoundException;
import com.andres.course.claude.springboot.springapi.app.mappers.UserMapper;
import com.andres.course.claude.springboot.springapi.app.models.Role;
import com.andres.course.claude.springboot.springapi.app.models.User;
import com.andres.course.claude.springboot.springapi.app.repositories.UserRepository;
import com.andres.course.claude.springboot.springapi.app.repositories.UserSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class UserServiceImpl implements UserService {

    /** Campos por los que se puede ordenar el listado ({@code ?sort=campo,asc|desc}); nunca password. */
    static final Set<String> SORT_FIELDS = Set.of("id", "email", "role", "enabled", "createdAt");

    static final String LAST_ADMIN_MESSAGE = "Debe quedar al menos un administrador habilitado";

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
    public Page<UserDto> findPage(UserFilter filter, Pageable pageable) {
        return userRepository.findAll(UserSpecifications.of(filter), Pageables.checked(pageable, SORT_FIELDS))
                .map(userMapper::toDto);
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
        // ¿Sigue siendo un ADMIN habilitado después del cambio? (enabled null conserva el estado actual)
        boolean staysActiveAdmin = dto.role() == Role.ADMIN
                && (dto.enabled() == null ? entity.isEnabled() : dto.enabled());
        ensureNotLastActiveAdmin(entity, staysActiveAdmin);

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
        ensureNotLastActiveAdmin(entity, false);
        // Sus refresh tokens se borran en la base de datos por ON DELETE CASCADE.
        userRepository.delete(entity);
    }

    /**
     * Impide dejar el sistema sin administradores: si el usuario es hoy un ADMIN habilitado y deja
     * de serlo (degradado, deshabilitado o borrado), debe quedar al menos otro ADMIN habilitado.
     *
     * Con la regla de "uno mismo" normalmente quien actúa es otro admin habilitado, así que hay al
     * menos dos. Pero el rol viaja en un JWT stateless: un admin recién degradado o deshabilitado
     * conserva el rol ADMIN en su token hasta 15 minutos. Esta regla cubre ese intervalo.
     */
    private void ensureNotLastActiveAdmin(User target, boolean staysActiveAdmin) {
        boolean isActiveAdmin = target.getRole() == Role.ADMIN && target.isEnabled();
        if (isActiveAdmin && !staysActiveAdmin && userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new OperationNotAllowedException(LAST_ADMIN_MESSAGE);
        }
    }

    private User getOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}

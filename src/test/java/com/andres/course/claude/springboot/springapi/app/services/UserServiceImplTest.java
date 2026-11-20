package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.UserDto;
import com.andres.course.claude.springboot.springapi.app.exceptions.OperationNotAllowedException;
import com.andres.course.claude.springboot.springapi.app.mappers.UserMapper;
import com.andres.course.claude.springboot.springapi.app.models.Role;
import com.andres.course.claude.springboot.springapi.app.models.User;
import com.andres.course.claude.springboot.springapi.app.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas de administradores de UserServiceImpl (uno mismo y último ADMIN habilitado), sin base de datos. */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String ACTOR = "admin@ejemplo.com";
    private static final String LAST_ADMIN = "Debe quedar al menos un administrador habilitado";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, new UserMapper(), passwordEncoder, refreshTokenService);
    }

    private static User user(long id, String email, Role role, boolean enabled) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setEnabled(enabled);
        return user;
    }

    private static UserDto changes(String email, Role role, Boolean enabled) {
        return new UserDto(null, email, null, role, enabled, null, null);
    }

    private void found(User user) {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private void activeAdmins(long count) {
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(count);
    }

    private void updateWillSave() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // --- Borrar ---

    @Test
    void borrarAlUltimoAdminHabilitadoResponde409() {
        User target = user(2L, "otro.admin@ejemplo.com", Role.ADMIN, true);
        found(target);
        activeAdmins(1);

        assertThatThrownBy(() -> userService.delete(2L, ACTOR))
                .isInstanceOf(OperationNotAllowedException.class)
                .hasMessage(LAST_ADMIN);
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void borrarUnAdminCuandoHayDosFunciona() {
        User target = user(2L, "otro.admin@ejemplo.com", Role.ADMIN, true);
        found(target);
        activeAdmins(2);

        userService.delete(2L, ACTOR);

        verify(userRepository).delete(target);
    }

    @Test
    void borrarUnAdminDeshabilitadoNoCuentaComoAdminActivo() {
        User target = user(2L, "admin.deshabilitado@ejemplo.com", Role.ADMIN, false);
        found(target);

        userService.delete(2L, ACTOR);

        verify(userRepository).delete(target);
        verify(userRepository, never()).countByRoleAndEnabledTrue(any());
    }

    @Test
    void borrarUnUserNoConsultaElConteoDeAdmins() {
        User target = user(3L, "user@ejemplo.com", Role.USER, true);
        found(target);

        userService.delete(3L, ACTOR);

        verify(userRepository).delete(target);
        verify(userRepository, never()).countByRoleAndEnabledTrue(any());
    }

    // --- Degradar o deshabilitar ---

    @Test
    void degradarAlUltimoAdminResponde409() {
        found(user(2L, "otro.admin@ejemplo.com", Role.ADMIN, true));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        activeAdmins(1);

        assertThatThrownBy(() -> userService.update(2L, changes("otro.admin@ejemplo.com", Role.USER, null), ACTOR))
                .isInstanceOf(OperationNotAllowedException.class)
                .hasMessage(LAST_ADMIN);
        verify(userRepository, never()).save(any(User.class));
        verify(refreshTokenService, never()).revokeAll(any(User.class));
    }

    @Test
    void deshabilitarAlUltimoAdminResponde409() {
        found(user(2L, "otro.admin@ejemplo.com", Role.ADMIN, true));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        activeAdmins(1);

        assertThatThrownBy(() -> userService.update(2L, changes("otro.admin@ejemplo.com", Role.ADMIN, false), ACTOR))
                .isInstanceOf(OperationNotAllowedException.class)
                .hasMessage(LAST_ADMIN);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void degradarUnAdminCuandoHayDosFuncionaYCierraSusSesiones() {
        User target = user(2L, "otro.admin@ejemplo.com", Role.ADMIN, true);
        found(target);
        updateWillSave();
        activeAdmins(2);

        UserDto result = userService.update(2L, changes("otro.admin@ejemplo.com", Role.USER, null), ACTOR);

        assertThat(result.role()).isEqualTo(Role.USER);
        verify(refreshTokenService).revokeAll(target);
    }

    @Test
    void editarElEmailDelUltimoAdminSinCambiarRolNiEstadoFunciona() {
        found(user(2L, "otro.admin@ejemplo.com", Role.ADMIN, true));
        updateWillSave();

        UserDto result = userService.update(2L, changes("nuevo.email@ejemplo.com", Role.ADMIN, null), ACTOR);

        assertThat(result.email()).isEqualTo("nuevo.email@ejemplo.com");
        verify(userRepository, never()).countByRoleAndEnabledTrue(any());
    }

    // --- Reglas de "uno mismo" (se evalúan antes y conservan su mensaje) ---

    @Test
    void lasReglasDeUnoMismoConservanSuMensaje() {
        User self = user(1L, ACTOR, Role.ADMIN, true);
        found(self);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(1L, ACTOR))
                .isInstanceOf(OperationNotAllowedException.class)
                .hasMessage("No puedes borrar tu propio usuario");
        assertThatThrownBy(() -> userService.update(1L, changes(ACTOR, Role.USER, null), ACTOR))
                .isInstanceOf(OperationNotAllowedException.class)
                .hasMessage("No puedes quitarte el rol ADMIN ni deshabilitar tu propio usuario");
        verify(userRepository, never()).countByRoleAndEnabledTrue(any());
    }
}

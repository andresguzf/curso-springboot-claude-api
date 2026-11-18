package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.exceptions.InvalidTokenException;
import com.andres.course.claude.springboot.springapi.app.models.RefreshToken;
import com.andres.course.claude.springboot.springapi.app.models.User;
import com.andres.course.claude.springboot.springapi.app.repositories.RefreshTokenRepository;
import com.andres.course.claude.springboot.springapi.app.security.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties("no-se-usa", "3-spring-api",
                Duration.ofMinutes(15), Duration.ofDays(7), true);
        service = new RefreshTokenServiceImpl(repository, properties);
        user = new User();
        user.setId(1L);
        user.setEmail("ana@ejemplo.com");
    }

    /** Emite un token y devuelve la entidad que se guardó en la base. */
    private RefreshToken issueAndCapture(String[] rawHolder) {
        rawHolder[0] = service.issue(user);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void guardaSoloElHashDelToken() {
        String[] raw = new String[1];
        RefreshToken saved = issueAndCapture(raw);

        assertThat(saved.getTokenHash()).hasSize(64).isNotEqualTo(raw[0]);
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(6)));
    }

    @Test
    void rotaElTokenRevocandoElAnterior() {
        String[] raw = new String[1];
        RefreshToken saved = issueAndCapture(raw);
        when(repository.findByTokenHash(saved.getTokenHash())).thenReturn(Optional.of(saved));

        RefreshTokenService.Rotation rotation = service.rotate(raw[0]);

        assertThat(saved.isRevoked()).isTrue();
        assertThat(rotation.user()).isSameAs(user);
        assertThat(rotation.refreshToken()).isNotEqualTo(raw[0]);
    }

    @Test
    void siSeReutilizaUnTokenRevocadoCierraTodasLasSesiones() {
        String[] raw = new String[1];
        RefreshToken saved = issueAndCapture(raw);
        saved.setRevoked(true);
        when(repository.findByTokenHash(saved.getTokenHash())).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.rotate(raw[0])).isInstanceOf(InvalidTokenException.class);
        verify(repository).revokeAllByUser(user);
    }

    @Test
    void rechazaTokenExpirado() {
        String[] raw = new String[1];
        RefreshToken saved = issueAndCapture(raw);
        saved.setExpiresAt(Instant.now().minusSeconds(1));
        when(repository.findByTokenHash(saved.getTokenHash())).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.rotate(raw[0])).isInstanceOf(InvalidTokenException.class);
        verify(repository, never()).revokeAllByUser(any());
    }

    @Test
    void rechazaTokenDesconocido() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("no-existe")).isInstanceOf(InvalidTokenException.class);
    }
}

package com.andres.course.claude.springboot.springapi.app.repositories;

import com.andres.course.claude.springboot.springapi.app.models.RefreshToken;
import com.andres.course.claude.springboot.springapi.app.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Revoca de una vez todos los tokens vigentes del usuario (logout global, cambio de rol, reuso detectado).
    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.user = :user and t.revoked = false")
    int revokeAllByUser(User user);

    // Limpieza periódica: borra los tokens vencidos. Los revocados se conservan hasta que vencen
    // para poder detectar si alguien intenta reutilizarlos (posible robo).
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    int deleteExpired(Instant now);
}

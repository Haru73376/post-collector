package com.github.haru73376.post_collector.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void deleteByTokenHash(String tokenHash);
    void deleteAllByUserId(UUID userId);
    void deleteAllByExpiresAtBefore(LocalDateTime now);
}

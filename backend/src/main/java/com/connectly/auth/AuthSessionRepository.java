package com.connectly.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    List<AuthSession> findByUserIdAndRevokedAtIsNullOrderByLastUsedAtDesc(Long userId);

    @Modifying
    @Query("UPDATE AuthSession s SET s.revokedAt = :now WHERE s.userId = :userId AND s.revokedAt IS NULL")
    void revokeAllForUser(Long userId, Instant now);

    @Modifying
    @Query("DELETE FROM AuthSession s WHERE s.userId = :userId")
    void deleteAllForUser(Long userId);

    @Modifying
    @Query("DELETE FROM AuthSession s WHERE s.expiresAt < :cutoff AND s.revokedAt IS NOT NULL")
    void purgeExpiredBefore(Instant cutoff);
}

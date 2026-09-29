package com.anjoguarda.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken token set token.revokedAt = :when where token.tokenHash = :hash and token.revokedAt is null")
    int revokeIfActive(String hash, Instant when);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken token set token.revokedAt = :when where token.userId = :userId and token.revokedAt is null")
    int revokeActiveByUserId(UUID userId, Instant when);
}

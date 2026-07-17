package com.arclume.api.repository;

import com.arclume.api.domain.EmailVerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    Optional<EmailVerificationToken> findTopByUserIdOrderByCreatedAtDesc(UUID userId);

    long deleteByExpiresAtBefore(Instant cutoff);
}

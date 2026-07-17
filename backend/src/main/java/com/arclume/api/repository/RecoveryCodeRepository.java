package com.arclume.api.repository;

import com.arclume.api.domain.RecoveryCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RecoveryCode> findByUserIdAndCodeHashAndUsedAtIsNull(UUID userId, String codeHash);

    List<RecoveryCode> findAllByUserId(UUID userId);

    long deleteByUserId(UUID userId);
}

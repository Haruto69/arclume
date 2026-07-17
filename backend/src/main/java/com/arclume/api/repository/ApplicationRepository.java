package com.arclume.api.repository;

import com.arclume.api.domain.Application;
import com.arclume.api.domain.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    @EntityGraph(attributePaths = "job")
    Page<Application> findByUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "job")
    Page<Application> findByUserIdAndStatus(UUID userId, ApplicationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "job")
    Optional<Application> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = "job")
    Optional<Application> findByUserIdAndJobId(UUID userId, UUID jobId);

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, ApplicationStatus status);
}

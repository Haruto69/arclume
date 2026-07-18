package com.arclume.api.repository;

import com.arclume.api.domain.Competition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CompetitionRepository extends JpaRepository<Competition, UUID>, JpaSpecificationExecutor<Competition> {
    Optional<Competition> findBySourceProviderAndSourceId(String sourceProvider, String sourceId);
}

package com.arclume.api.repository;

import com.arclume.api.domain.Hackathon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface HackathonRepository extends JpaRepository<Hackathon, UUID>, JpaSpecificationExecutor<Hackathon> {
    Optional<Hackathon> findBySourceProviderAndSourceId(String sourceProvider, String sourceId);
}


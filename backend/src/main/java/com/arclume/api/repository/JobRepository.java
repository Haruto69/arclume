package com.arclume.api.repository;

import com.arclume.api.domain.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {
    java.util.Optional<Job> findBySourceProviderAndExternalId(String sourceProvider, String externalId);
}

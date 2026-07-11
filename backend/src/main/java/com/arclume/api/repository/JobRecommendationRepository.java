package com.arclume.api.repository;

import com.arclume.api.domain.JobRecommendation;
import com.arclume.api.domain.RecommendationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JobRecommendationRepository extends JpaRepository<JobRecommendation, UUID>, JpaSpecificationExecutor<JobRecommendation> {
    Optional<JobRecommendation> findByUserIdAndJobId(UUID userId, UUID jobId);

    Optional<JobRecommendation> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndJobId(UUID userId, UUID jobId);

    long countByUserIdAndStatus(UUID userId, RecommendationStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update job_recommendations
            set status = cast(:#{#status.name()} as varchar)
            where user_id = :userId
              and job_id in (select id from jobs where is_active = false)
              and status <> cast(:#{#status.name()} as varchar)
            """, nativeQuery = true)
    int markInactiveJobsAs(@Param("userId") UUID userId, @Param("status") RecommendationStatus status);
}



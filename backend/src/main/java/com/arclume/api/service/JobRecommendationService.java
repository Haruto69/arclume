package com.arclume.api.service;

import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.Job;
import com.arclume.api.domain.JobRecommendation;
import com.arclume.api.domain.RecommendationStatus;
import com.arclume.api.domain.User;
import com.arclume.api.domain.WorkMode;
import com.arclume.api.dto.CareerMatchResponse;
import com.arclume.api.dto.RecommendationRefreshResponse;
import com.arclume.api.dto.RecommendationResponse;
import com.arclume.api.repository.JobRecommendationRepository;
import com.arclume.api.repository.JobRecommendationSpecifications;
import com.arclume.api.repository.JobRepository;
import com.arclume.api.service.ai.CareerMatchingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JobRecommendationService {

    private final JobRecommendationRepository recommendationRepository;
    private final JobRepository jobRepository;
    private final CareerMatchingService careerMatchingService;

    public JobRecommendationService(
            JobRecommendationRepository recommendationRepository,
            JobRepository jobRepository,
            CareerMatchingService careerMatchingService) {
        this.recommendationRepository = recommendationRepository;
        this.jobRepository = jobRepository;
        this.careerMatchingService = careerMatchingService;
    }

    @Transactional
    public JobRecommendation createOrUpdateRecommendation(User user, UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        if (!Boolean.TRUE.equals(job.getActive())) {
            throw new IllegalArgumentException("Cannot recommend an inactive job");
        }

        CareerMatchResponse match = careerMatchingService.matchUserToJob(jobId, user);
        JobRecommendation recommendation = recommendationRepository.findByUserIdAndJobId(user.getId(), jobId)
                .orElseGet(JobRecommendation::new);
        boolean isNew = recommendation.getId() == null;
        recommendation.setUser(user);
        recommendation.setJob(job);
        recommendation.setMatchScore(match.getMatchScore());
        recommendation.setMatchedSkills(match.getMatchedSkills());
        recommendation.setMissingSkills(match.getMissingSkills());
        recommendation.setExplanation(match.getExplanation());
        recommendation.setGeneratedAt(Instant.now());
        if (isNew || recommendation.getStatus() == RecommendationStatus.EXPIRED) {
            recommendation.setStatus(RecommendationStatus.ACTIVE);
        }
        return recommendationRepository.saveAndFlush(recommendation);
    }

    @Transactional(readOnly = true)
    public Page<RecommendationResponse> listRecommendations(
            User user,
            RecommendationStatus status,
            Integer minimumScore,
            WorkMode workMode,
            EmploymentType employmentType,
            String company,
            String location,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort(sortBy, direction));
        Specification<JobRecommendation> spec = Specification.where(JobRecommendationSpecifications.belongsToUser(user.getId()))
                .and(JobRecommendationSpecifications.hasStatus(status))
                .and(JobRecommendationSpecifications.hasMinimumScore(minimumScore))
                .and(JobRecommendationSpecifications.hasWorkMode(workMode))
                .and(JobRecommendationSpecifications.hasEmploymentType(employmentType))
                .and(JobRecommendationSpecifications.hasCompany(company))
                .and(JobRecommendationSpecifications.hasLocation(location));

        return recommendationRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RecommendationResponse getRecommendation(User user, UUID recommendationId) {
        return recommendationRepository.findByIdAndUserId(recommendationId, user.getId())
                .map(this::toResponse)
                .orElseThrow(() -> new AccessDeniedException("Recommendation not found"));
    }

    @Transactional
    public RecommendationResponse updateStatus(User user, UUID recommendationId, RecommendationStatus status) {
        JobRecommendation recommendation = recommendationRepository.findByIdAndUserId(recommendationId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Recommendation not found"));
        recommendation.setStatus(status);
        return toResponse(recommendationRepository.saveAndFlush(recommendation));
    }

    @Transactional
    public RecommendationRefreshResponse refreshRecommendations(User user) {
        int expired = recommendationRepository.markInactiveJobsAs(user.getId(), RecommendationStatus.EXPIRED);
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;

        List<Job> jobs = jobRepository.findAll().stream()
                .filter(job -> Boolean.TRUE.equals(job.getActive()))
                .toList();

        for (Job job : jobs) {
            try {
                boolean exists = recommendationRepository.existsByUserIdAndJobId(user.getId(), job.getId());
                createOrUpdateRecommendation(user, job.getId());
                if (exists) {
                    updated++;
                } else {
                    created++;
                }
            } catch (IllegalArgumentException ex) {
                skipped++;
            } catch (Exception ex) {
                failed++;
            }
        }

        return new RecommendationRefreshResponse(created, updated, skipped, expired, failed);
    }

    private Sort sort(String sortBy, Sort.Direction direction) {
        Sort.Direction safeDirection = direction == null ? Sort.Direction.DESC : direction;
        String property = switch (sortBy == null ? "" : sortBy) {
            case "matchScore", "match_score", "score" -> "matchScore";
            case "generatedAt", "generated_at", "generatedDate", "generated_date" -> "generatedAt";
            case "updatedAt", "updated_at", "updatedDate", "updated_date" -> "updatedAt";
            default -> "generatedAt";
        };
        return Sort.by(safeDirection, property);
    }

    private RecommendationResponse toResponse(JobRecommendation recommendation) {
        Job job = recommendation.getJob();
        return new RecommendationResponse(
                recommendation.getId(),
                job.getId(),
                job.getTitle(),
                job.getCompany(),
                job.getLocation(),
                job.getEmploymentType(),
                job.getWorkMode(),
                recommendation.getMatchScore(),
                recommendation.getMatchedSkills(),
                recommendation.getMissingSkills(),
                recommendation.getExplanation(),
                recommendation.getStatus(),
                recommendation.getGeneratedAt(),
                recommendation.getUpdatedAt()
        );
    }
}

package com.arclume.api.repository;

import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.Job;
import com.arclume.api.domain.JobRecommendation;
import com.arclume.api.domain.RecommendationStatus;
import com.arclume.api.domain.WorkMode;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class JobRecommendationSpecifications {

    public static Specification<JobRecommendation> belongsToUser(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<JobRecommendation> hasStatus(RecommendationStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<JobRecommendation> hasMinimumScore(Integer minimumScore) {
        return (root, query, cb) -> minimumScore == null ? null : cb.greaterThanOrEqualTo(root.get("matchScore"), minimumScore);
    }

    public static Specification<JobRecommendation> hasWorkMode(WorkMode workMode) {
        return (root, query, cb) -> workMode == null ? null : cb.equal(job(root).get("workMode"), workMode);
    }

    public static Specification<JobRecommendation> hasEmploymentType(EmploymentType employmentType) {
        return (root, query, cb) -> employmentType == null ? null : cb.equal(job(root).get("employmentType"), employmentType);
    }

    public static Specification<JobRecommendation> hasCompany(String company) {
        return (root, query, cb) -> company == null || company.trim().isEmpty() ? null :
                cb.like(cb.lower(job(root).get("company")), "%" + company.toLowerCase() + "%");
    }

    public static Specification<JobRecommendation> hasLocation(String location) {
        return (root, query, cb) -> location == null || location.trim().isEmpty() ? null :
                cb.like(cb.lower(job(root).get("location")), "%" + location.toLowerCase() + "%");
    }

    private static Join<JobRecommendation, Job> job(jakarta.persistence.criteria.Root<JobRecommendation> root) {
        return root.join("job");
    }
}

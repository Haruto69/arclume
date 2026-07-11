package com.arclume.api.dto;

import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.RecommendationStatus;
import com.arclume.api.domain.WorkMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecommendationResponse(
        UUID id,
        UUID jobId,
        String jobTitle,
        String companyName,
        String location,
        EmploymentType employmentType,
        WorkMode workMode,
        int matchScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        String explanation,
        RecommendationStatus status,
        Instant generatedAt,
        Instant updatedAt
) {
}

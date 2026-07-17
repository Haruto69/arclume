package com.arclume.api.dto;

import com.arclume.api.domain.ApplicationStatus;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.WorkMode;

import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID jobId,
        String jobTitle,
        String companyName,
        String location,
        EmploymentType employmentType,
        WorkMode workMode,
        String externalUrl,
        String sourceProvider,
        ApplicationStatus status,
        Instant appliedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {
}

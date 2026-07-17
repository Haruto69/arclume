package com.arclume.api.dto;

import com.arclume.api.domain.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record ApplicationCreateRequest(
        @NotNull(message = "Job id is required")
        UUID jobId,
        ApplicationStatus status,
        Instant appliedAt,
        @Size(max = 2000, message = "Notes must be 2000 characters or fewer")
        String notes) {
}

package com.arclume.api.dto;

import com.arclume.api.domain.ApplicationStatus;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ApplicationUpdateRequest(
        ApplicationStatus status,
        Instant appliedAt,
        Boolean clearAppliedAt,
        @Size(max = 2000, message = "Notes must be 2000 characters or fewer")
        String notes) {
}

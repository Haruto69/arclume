package com.arclume.api.dto;

import com.arclume.api.domain.ApplicationStatus;

import java.util.Map;

public record ApplicationSummaryResponse(
        long total,
        Map<ApplicationStatus, Long> byStatus) {
}

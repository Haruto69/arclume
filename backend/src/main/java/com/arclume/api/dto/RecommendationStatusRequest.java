package com.arclume.api.dto;

import com.arclume.api.domain.RecommendationStatus;
import jakarta.validation.constraints.NotNull;

public class RecommendationStatusRequest {
    @NotNull
    private RecommendationStatus status;

    public RecommendationStatus getStatus() {
        return status;
    }

    public void setStatus(RecommendationStatus status) {
        this.status = status;
    }
}

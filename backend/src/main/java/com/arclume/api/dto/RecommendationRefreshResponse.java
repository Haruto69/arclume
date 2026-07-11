package com.arclume.api.dto;

public record RecommendationRefreshResponse(
        int created,
        int updated,
        int skipped,
        int expired,
        int failed
) {
}

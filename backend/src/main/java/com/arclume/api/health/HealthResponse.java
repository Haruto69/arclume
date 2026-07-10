package com.arclume.api.health;

import java.time.Instant;

public record HealthResponse(
        String application,
        String status,
        String version,
        Instant timestamp
) {}

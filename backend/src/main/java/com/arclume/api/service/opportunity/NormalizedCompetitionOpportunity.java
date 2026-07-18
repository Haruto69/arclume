package com.arclume.api.service.opportunity;

import com.arclume.api.domain.CompetitionFormat;
import com.arclume.api.domain.CompetitionPhase;
import com.arclume.api.domain.OpportunityCategory;

import java.time.Instant;

public record NormalizedCompetitionOpportunity(
        String sourceId,
        String sourceUrl,
        String attributionLabel,
        String title,
        String organizer,
        CompetitionFormat competitionFormat,
        CompetitionPhase phase,
        String kind,
        Integer difficulty,
        String city,
        String country,
        Instant startsAt,
        Instant endsAt,
        long durationSeconds,
        boolean active
) implements NormalizedOpportunityRecord {

    public NormalizedCompetitionOpportunity {
        requireNonBlank(sourceId, "sourceId", 200);
        requireNonBlank(sourceUrl, "sourceUrl", 1000);
        requireOptional(attributionLabel, "attributionLabel", 120);
        requireNonBlank(title, "title", 250);
        requireNonBlank(organizer, "organizer", 200);
        requireNonNull(competitionFormat, "competitionFormat");
        requireNonNull(phase, "phase");
        requireOptional(kind, "kind", 200);
        requireOptional(city, "city", 150);
        requireOptional(country, "country", 150);
        requireNonNull(startsAt, "startsAt");
        requireNonNull(endsAt, "endsAt");
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        if (endsAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("endsAt cannot be before startsAt");
        }
        if (difficulty != null && (difficulty < 1 || difficulty > 5)) {
            throw new IllegalArgumentException("difficulty must be between 1 and 5");
        }
    }

    @Override
    public OpportunityCategory category() {
        return OpportunityCategory.COMPETITION;
    }

    private static void requireNonBlank(String value, String field, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds maximum length");
        }
    }

    private static void requireOptional(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds maximum length");
        }
    }

    private static void requireNonNull(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}

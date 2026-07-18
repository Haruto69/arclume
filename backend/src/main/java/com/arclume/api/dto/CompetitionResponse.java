package com.arclume.api.dto;

import com.arclume.api.domain.Competition;
import com.arclume.api.domain.CompetitionFormat;
import com.arclume.api.domain.CompetitionPhase;

import java.time.Instant;
import java.util.UUID;

public record CompetitionResponse(
        UUID id,
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
        String externalUrl,
        String sourceProvider,
        String sourceId,
        String attributionLabel,
        Instant syncedAt,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static CompetitionResponse from(Competition competition) {
        return new CompetitionResponse(
                competition.getId(),
                competition.getTitle(),
                competition.getOrganizer(),
                competition.getCompetitionFormat(),
                competition.getPhase(),
                competition.getKind(),
                competition.getDifficulty(),
                competition.getCity(),
                competition.getCountry(),
                competition.getStartsAt(),
                competition.getEndsAt(),
                competition.getDurationSeconds(),
                competition.getExternalUrl(),
                competition.getSourceProvider(),
                competition.getSourceId(),
                competition.getAttributionLabel(),
                competition.getSyncedAt(),
                Boolean.TRUE.equals(competition.getActive()),
                competition.getCreatedAt(),
                competition.getUpdatedAt()
        );
    }
}

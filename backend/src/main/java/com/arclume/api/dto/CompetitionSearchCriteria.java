package com.arclume.api.dto;

import com.arclume.api.domain.CompetitionFormat;
import com.arclume.api.domain.CompetitionPhase;

import java.time.Instant;

public record CompetitionSearchCriteria(
        String keyword,
        String sourceProvider,
        CompetitionFormat competitionFormat,
        CompetitionPhase phase,
        String country,
        String city,
        Instant startsAfter,
        Instant startsBefore,
        Boolean active
) {
}

package com.arclume.api.dto;

import com.arclume.api.domain.HackathonMode;
import com.arclume.api.domain.HackathonOrganizerType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HackathonSearchCriteria(
        String keyword,
        String organizer,
        String city,
        String region,
        String country,
        String sourceProvider,
        HackathonMode mode,
        HackathonOrganizerType organizerType,
        BigDecimal minPrizePoolAmount,
        BigDecimal maxPrizePoolAmount,
        LocalDate startsAfter,
        LocalDate startsBefore,
        Boolean active
) {
}


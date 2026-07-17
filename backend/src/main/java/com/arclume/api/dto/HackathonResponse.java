package com.arclume.api.dto;

import com.arclume.api.domain.Hackathon;
import com.arclume.api.domain.HackathonMode;
import com.arclume.api.domain.HackathonOrganizerType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HackathonResponse(
        UUID id,
        String title,
        String organizer,
        HackathonOrganizerType organizerType,
        String city,
        String region,
        String country,
        HackathonMode mode,
        BigDecimal prizePoolAmount,
        String prizePoolCurrency,
        LocalDate registrationDeadline,
        LocalDate startDate,
        LocalDate endDate,
        String externalUrl,
        String sourceProvider,
        String description,
        List<String> tags,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static HackathonResponse from(Hackathon hackathon) {
        List<String> tags = hackathon.getTags() == null ? List.of() : List.copyOf(hackathon.getTags());
        return new HackathonResponse(
                hackathon.getId(),
                hackathon.getTitle(),
                hackathon.getOrganizer(),
                hackathon.getOrganizerType(),
                hackathon.getCity(),
                hackathon.getRegion(),
                hackathon.getCountry(),
                hackathon.getMode(),
                hackathon.getPrizePoolAmount(),
                hackathon.getPrizePoolCurrency(),
                hackathon.getRegistrationDeadline(),
                hackathon.getStartDate(),
                hackathon.getEndDate(),
                hackathon.getExternalUrl(),
                hackathon.getSourceProvider(),
                hackathon.getDescription(),
                tags,
                Boolean.TRUE.equals(hackathon.getActive()),
                hackathon.getCreatedAt(),
                hackathon.getUpdatedAt()
        );
    }
}


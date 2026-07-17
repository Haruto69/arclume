package com.arclume.api.dto;

import com.arclume.api.domain.StudentProgram;
import com.arclume.api.domain.StudentProgramMode;
import com.arclume.api.domain.StudentProgramType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StudentProgramResponse(
        UUID id,
        String title,
        String company,
        StudentProgramType programType,
        StudentProgramMode mode,
        String region,
        String country,
        String eligibility,
        String benefitSummary,
        LocalDate applicationDeadline,
        LocalDate startDate,
        LocalDate endDate,
        boolean alwaysOpen,
        String externalUrl,
        String sourceProvider,
        String description,
        List<String> benefitTypes,
        List<String> tags,
        boolean active,
        LocalDate lastVerifiedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static StudentProgramResponse from(StudentProgram program) {
        List<String> benefitTypes = program.getBenefitTypes() == null
                ? List.of()
                : List.copyOf(program.getBenefitTypes());
        List<String> tags = program.getTags() == null ? List.of() : List.copyOf(program.getTags());
        return new StudentProgramResponse(
                program.getId(),
                program.getTitle(),
                program.getCompany(),
                program.getProgramType(),
                program.getMode(),
                program.getRegion(),
                program.getCountry(),
                program.getEligibility(),
                program.getBenefitSummary(),
                program.getApplicationDeadline(),
                program.getStartDate(),
                program.getEndDate(),
                Boolean.TRUE.equals(program.getAlwaysOpen()),
                program.getExternalUrl(),
                program.getSourceProvider(),
                program.getDescription(),
                benefitTypes,
                tags,
                Boolean.TRUE.equals(program.getActive()),
                program.getLastVerifiedAt(),
                program.getCreatedAt(),
                program.getUpdatedAt()
        );
    }
}

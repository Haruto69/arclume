package com.arclume.api.dto;

import com.arclume.api.domain.BenefitType;
import com.arclume.api.domain.StudentProgramMode;
import com.arclume.api.domain.StudentProgramType;

import java.time.LocalDate;

public record StudentProgramSearchCriteria(
        String keyword,
        String company,
        StudentProgramType programType,
        StudentProgramMode mode,
        BenefitType benefitType,
        String region,
        String country,
        Boolean alwaysOpen,
        Boolean active,
        LocalDate applicationDeadlineBefore,
        LocalDate applicationDeadlineAfter
) {
}

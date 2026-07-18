package com.arclume.api.service.opportunity;

import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.OpportunityCategory;
import com.arclume.api.domain.WorkMode;

import java.time.Instant;

public record NormalizedJobOpportunity(
        String sourceId,
        String sourceUrl,
        String attributionLabel,
        String title,
        String company,
        String location,
        EmploymentType employmentType,
        WorkMode workMode,
        String salaryRange,
        Instant postedAt,
        String description,
        String requirements,
        boolean active
) implements NormalizedOpportunityRecord {

    @Override
    public OpportunityCategory category() {
        return OpportunityCategory.JOB;
    }
}

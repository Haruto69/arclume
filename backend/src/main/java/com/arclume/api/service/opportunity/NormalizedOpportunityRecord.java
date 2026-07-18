package com.arclume.api.service.opportunity;

import com.arclume.api.domain.OpportunityCategory;

public interface NormalizedOpportunityRecord {
    OpportunityCategory category();

    String sourceId();

    String sourceUrl();

    String attributionLabel();
}

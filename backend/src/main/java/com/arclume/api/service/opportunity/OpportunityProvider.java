package com.arclume.api.service.opportunity;

import com.arclume.api.domain.OpportunityCategory;

public interface OpportunityProvider {

    String providerKey();

    OpportunityCategory category();

    boolean isEnabled();

    OpportunityProviderFetchResult fetchOpportunities();
}

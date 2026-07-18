package com.arclume.api.service.opportunity;

import java.util.List;

public record OpportunityProviderFetchResult(
        int recordsFetched,
        int recordsSkipped,
        List<NormalizedOpportunityRecord> records
) {

    public OpportunityProviderFetchResult {
        requireNonNegative(recordsFetched, "recordsFetched");
        requireNonNegative(recordsSkipped, "recordsSkipped");
        records = records == null ? List.of() : List.copyOf(records);
    }

    public static OpportunityProviderFetchResult empty() {
        return new OpportunityProviderFetchResult(0, 0, List.of());
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
    }
}

package com.arclume.api.domain;

public record OpportunitySyncCounters(
        int recordsFetched,
        int recordsCreated,
        int recordsUpdated,
        int recordsSkipped,
        int recordsFailed,
        int recordsDeactivated
) {

    public OpportunitySyncCounters {
        requireNonNegative(recordsFetched, "recordsFetched");
        requireNonNegative(recordsCreated, "recordsCreated");
        requireNonNegative(recordsUpdated, "recordsUpdated");
        requireNonNegative(recordsSkipped, "recordsSkipped");
        requireNonNegative(recordsFailed, "recordsFailed");
        requireNonNegative(recordsDeactivated, "recordsDeactivated");
    }

    public static OpportunitySyncCounters empty() {
        return new OpportunitySyncCounters(0, 0, 0, 0, 0, 0);
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
    }
}

package com.arclume.api.dto;

import com.arclume.api.domain.OpportunityCategory;
import com.arclume.api.domain.OpportunitySyncRun;
import com.arclume.api.domain.OpportunitySyncStatus;

import java.time.Instant;
import java.util.UUID;

public record OpportunitySyncResponse(
        UUID syncRunId,
        String providerKey,
        OpportunityCategory category,
        OpportunitySyncStatus status,
        Instant startedAt,
        Instant finishedAt,
        int recordsFetched,
        int recordsCreated,
        int recordsUpdated,
        int recordsSkipped,
        int recordsFailed,
        int recordsDeactivated,
        String syncError
) {

    public static OpportunitySyncResponse from(OpportunitySyncRun run) {
        return new OpportunitySyncResponse(
                run.getId(),
                run.getProviderKey(),
                run.getCategory(),
                run.getStatus(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getRecordsFetched(),
                run.getRecordsCreated(),
                run.getRecordsUpdated(),
                run.getRecordsSkipped(),
                run.getRecordsFailed(),
                run.getRecordsDeactivated(),
                run.getSyncError()
        );
    }
}

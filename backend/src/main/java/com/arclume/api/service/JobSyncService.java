package com.arclume.api.service;

import com.arclume.api.dto.JobSyncSummary;
import com.arclume.api.dto.OpportunitySyncResponse;
import com.arclume.api.service.opportunity.OpportunitySyncService;
import com.arclume.api.service.opportunity.RemotiveJobProvider;
import org.springframework.stereotype.Service;

@Service
public class JobSyncService {

    private final OpportunitySyncService opportunitySyncService;

    public JobSyncService(OpportunitySyncService opportunitySyncService) {
        this.opportunitySyncService = opportunitySyncService;
    }

    public JobSyncSummary syncJobs() {
        return toSummary(syncRemotive());
    }

    public OpportunitySyncResponse syncRemotive() {
        return opportunitySyncService.sync(RemotiveJobProvider.PROVIDER_KEY);
    }

    public static JobSyncSummary toSummary(OpportunitySyncResponse response) {
        return new JobSyncSummary(
                response.recordsFetched(),
                response.recordsCreated(),
                response.recordsUpdated(),
                response.recordsSkipped(),
                response.recordsFailed()
        );
    }
}

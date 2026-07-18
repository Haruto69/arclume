package com.arclume.api.service.opportunity;

import com.arclume.api.domain.OpportunityCategory;
import com.arclume.api.domain.OpportunitySyncCounters;
import com.arclume.api.domain.OpportunitySyncRun;
import com.arclume.api.domain.OpportunitySyncStatus;
import com.arclume.api.repository.OpportunitySyncRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OpportunitySyncRunLifecycleService {

    private final OpportunitySyncRunRepository syncRunRepository;

    public OpportunitySyncRunLifecycleService(OpportunitySyncRunRepository syncRunRepository) {
        this.syncRunRepository = syncRunRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OpportunitySyncRun start(String providerKey, OpportunityCategory category, Instant startedAt) {
        return syncRunRepository.saveAndFlush(OpportunitySyncRun.start(providerKey, category, startedAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OpportunitySyncRun finish(
            UUID runId,
            OpportunitySyncStatus status,
            OpportunitySyncCounters counters,
            Instant finishedAt,
            String error) {
        OpportunitySyncRun run = syncRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Sync run not found: " + runId));
        run.complete(status, counters, finishedAt, error);
        return syncRunRepository.saveAndFlush(run);
    }
}

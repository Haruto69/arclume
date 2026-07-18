package com.arclume.api.service.opportunity;

import com.arclume.api.domain.OpportunitySyncCounters;
import com.arclume.api.domain.OpportunitySyncRun;
import com.arclume.api.domain.OpportunitySyncStatus;
import com.arclume.api.dto.OpportunitySyncResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class OpportunitySyncService {

    private static final Logger log = LoggerFactory.getLogger(OpportunitySyncService.class);

    private final OpportunityProviderRegistry providerRegistry;
    private final OpportunitySyncRunLifecycleService lifecycleService;
    private final JobOpportunityWriter jobOpportunityWriter;
    private final CompetitionOpportunityWriter competitionOpportunityWriter;

    public OpportunitySyncService(
            OpportunityProviderRegistry providerRegistry,
            OpportunitySyncRunLifecycleService lifecycleService,
            JobOpportunityWriter jobOpportunityWriter,
            CompetitionOpportunityWriter competitionOpportunityWriter) {
        this.providerRegistry = providerRegistry;
        this.lifecycleService = lifecycleService;
        this.jobOpportunityWriter = jobOpportunityWriter;
        this.competitionOpportunityWriter = competitionOpportunityWriter;
    }

    public OpportunitySyncResponse sync(String providerKey) {
        OpportunityProvider provider = providerRegistry.getRequired(providerKey);
        Instant startedAt = Instant.now();
        OpportunitySyncRun run = lifecycleService.start(provider.providerKey(), provider.category(), startedAt);

        if (!provider.isEnabled()) {
            OpportunitySyncRun skipped = lifecycleService.finish(
                    run.getId(),
                    OpportunitySyncStatus.SKIPPED,
                    OpportunitySyncCounters.empty(),
                    Instant.now(),
                    "Provider " + provider.providerKey() + " is disabled");
            return OpportunitySyncResponse.from(skipped);
        }

        try {
            OpportunityProviderFetchResult fetchResult = provider.fetchOpportunities();
            OpportunitySyncCounters counters = persist(provider, fetchResult, startedAt);
            OpportunitySyncStatus status = counters.recordsFailed() > 0
                    ? OpportunitySyncStatus.PARTIAL
                    : OpportunitySyncStatus.SUCCEEDED;
            String error = counters.recordsFailed() > 0
                    ? counters.recordsFailed() + " opportunity records or sources failed to persist or fetch"
                    : null;

            OpportunitySyncRun completed = lifecycleService.finish(
                    run.getId(),
                    status,
                    counters,
                    Instant.now(),
                    error);
            return OpportunitySyncResponse.from(completed);
        } catch (Exception e) {
            log.warn("Opportunity provider sync failed for provider {}", provider.providerKey());
            OpportunitySyncCounters counters = new OpportunitySyncCounters(0, 0, 0, 0, 1, 0);
            OpportunitySyncRun failed = lifecycleService.finish(
                    run.getId(),
                    OpportunitySyncStatus.FAILED,
                    counters,
                    Instant.now(),
                    safeFailureSummary(e));
            return OpportunitySyncResponse.from(failed);
        }
    }

    private OpportunitySyncCounters persist(
            OpportunityProvider provider,
            OpportunityProviderFetchResult fetchResult,
            Instant syncedAt) {
        int created = 0;
        int updated = 0;
        int failed = fetchResult.recordsFailed();

        for (NormalizedOpportunityRecord record : fetchResult.records()) {
            try {
                OpportunityPersistResult result = persistRecord(provider, record, syncedAt);
                if (result == OpportunityPersistResult.CREATED) {
                    created++;
                } else {
                    updated++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("Opportunity record failed to persist for provider {}", provider.providerKey());
            }
        }

        return new OpportunitySyncCounters(
                fetchResult.recordsFetched(),
                created,
                updated,
                fetchResult.recordsSkipped(),
                failed,
                0
        );
    }

    private OpportunityPersistResult persistRecord(
            OpportunityProvider provider,
            NormalizedOpportunityRecord record,
            Instant syncedAt) {
        if (record.category() != provider.category()) {
            throw new IllegalArgumentException("Provider returned a record for the wrong category");
        }
        return switch (record.category()) {
            case JOB -> jobOpportunityWriter.upsert(
                    provider.providerKey(),
                    (NormalizedJobOpportunity) record,
                    syncedAt);
            case COMPETITION -> competitionOpportunityWriter.upsert(
                    provider.providerKey(),
                    (NormalizedCompetitionOpportunity) record,
                    syncedAt);
            case HACKATHON, EVENT, STUDENT_PROGRAM ->
                    throw new IllegalArgumentException("No persistence writer exists for " + record.category());
        };
    }

    private String safeFailureSummary(Exception e) {
        return "Provider sync failed: " + e.getClass().getSimpleName();
    }
}

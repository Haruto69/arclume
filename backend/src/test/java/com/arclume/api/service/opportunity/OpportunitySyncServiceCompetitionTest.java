package com.arclume.api.service.opportunity;

import com.arclume.api.domain.CompetitionFormat;
import com.arclume.api.domain.CompetitionPhase;
import com.arclume.api.domain.OpportunityCategory;
import com.arclume.api.domain.OpportunitySyncCounters;
import com.arclume.api.domain.OpportunitySyncRun;
import com.arclume.api.domain.OpportunitySyncStatus;
import com.arclume.api.dto.OpportunitySyncResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpportunitySyncServiceCompetitionTest {

    @Test
    void competitionPersistenceFailureProducesPartialCountersWithoutDeactivation() {
        NormalizedCompetitionOpportunity record = new NormalizedCompetitionOpportunity(
                "1234",
                "https://codeforces.com/contest/1234",
                "Contest data from Codeforces",
                "Codeforces Round",
                "Codeforces",
                CompetitionFormat.CF,
                CompetitionPhase.BEFORE,
                "Official",
                2,
                "Warsaw",
                "Poland",
                Instant.parse("2026-08-01T12:00:00Z"),
                Instant.parse("2026-08-01T14:00:00Z"),
                7200,
                true
        );
        OpportunityProvider provider = new FakeProvider(List.of(record));
        OpportunityProviderRegistry registry = new OpportunityProviderRegistry(List.of(provider));
        OpportunitySyncRunLifecycleService lifecycleService = mock(OpportunitySyncRunLifecycleService.class);
        JobOpportunityWriter jobOpportunityWriter = mock(JobOpportunityWriter.class);
        CompetitionOpportunityWriter competitionOpportunityWriter = mock(CompetitionOpportunityWriter.class);
        OpportunitySyncService service = new OpportunitySyncService(
                registry,
                lifecycleService,
                jobOpportunityWriter,
                competitionOpportunityWriter);
        OpportunitySyncRun started = OpportunitySyncRun.start("CODEFORCES", OpportunityCategory.COMPETITION, Instant.now());
        started.setId(UUID.randomUUID());

        when(lifecycleService.start(eq("CODEFORCES"), eq(OpportunityCategory.COMPETITION), any()))
                .thenReturn(started);
        when(competitionOpportunityWriter.upsert(eq("CODEFORCES"), eq(record), any()))
                .thenThrow(new RuntimeException("database unavailable"));
        when(lifecycleService.finish(
                eq(started.getId()),
                eq(OpportunitySyncStatus.PARTIAL),
                any(OpportunitySyncCounters.class),
                any(),
                eq("1 opportunity records or sources failed to persist or fetch")))
                .thenAnswer(invocation -> {
                    OpportunitySyncCounters counters = invocation.getArgument(2);
                    Instant finishedAt = invocation.getArgument(3);
                    String error = invocation.getArgument(4);
                    started.complete(OpportunitySyncStatus.PARTIAL, counters, finishedAt, error);
                    return started;
                });

        OpportunitySyncResponse response = service.sync("codeforces");

        assertThat(response.status()).isEqualTo(OpportunitySyncStatus.PARTIAL);
        assertThat(response.recordsFetched()).isEqualTo(1);
        assertThat(response.recordsCreated()).isZero();
        assertThat(response.recordsUpdated()).isZero();
        assertThat(response.recordsSkipped()).isZero();
        assertThat(response.recordsFailed()).isEqualTo(1);
        assertThat(response.recordsDeactivated()).isZero();
        assertThat(response.syncError()).isEqualTo("1 opportunity records or sources failed to persist or fetch");
        verify(competitionOpportunityWriter).upsert(eq("CODEFORCES"), eq(record), any());
    }

    private record FakeProvider(List<NormalizedOpportunityRecord> records) implements OpportunityProvider {

        @Override
        public String providerKey() {
            return "CODEFORCES";
        }

        @Override
        public OpportunityCategory category() {
            return OpportunityCategory.COMPETITION;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public OpportunityProviderFetchResult fetchOpportunities() {
            return new OpportunityProviderFetchResult(records.size(), 0, records);
        }
    }
}

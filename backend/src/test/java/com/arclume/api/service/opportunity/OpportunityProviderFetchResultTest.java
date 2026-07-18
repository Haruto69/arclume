package com.arclume.api.service.opportunity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpportunityProviderFetchResultTest {

    @Test
    void threeArgumentConstructorPreservesExistingZeroFailureBehavior() {
        OpportunityProviderFetchResult result = new OpportunityProviderFetchResult(2, 1, List.of());

        assertThat(result.recordsFetched()).isEqualTo(2);
        assertThat(result.recordsSkipped()).isEqualTo(1);
        assertThat(result.recordsFailed()).isZero();
        assertThat(result.records()).isEmpty();
    }

    @Test
    void fourArgumentConstructorCopiesRecordsAndAcceptsSourceFailures() {
        List<NormalizedOpportunityRecord> records = new ArrayList<>();
        NormalizedJobOpportunity job = new NormalizedJobOpportunity(
                "source-1",
                "https://example.test/job/source-1",
                "Attribution",
                "Developer",
                "Example Co",
                null,
                null,
                null,
                null,
                null,
                null,
                "",
                true);
        records.add(job);

        OpportunityProviderFetchResult result = new OpportunityProviderFetchResult(1, 0, 2, records);
        records.clear();

        assertThat(result.recordsFetched()).isEqualTo(1);
        assertThat(result.recordsSkipped()).isZero();
        assertThat(result.recordsFailed()).isEqualTo(2);
        assertThat(result.records()).containsExactly(job);
        assertThatThrownBy(() -> result.records().add(job))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyResultHasZeroCounters() {
        OpportunityProviderFetchResult result = OpportunityProviderFetchResult.empty();

        assertThat(result.recordsFetched()).isZero();
        assertThat(result.recordsSkipped()).isZero();
        assertThat(result.recordsFailed()).isZero();
        assertThat(result.records()).isEmpty();
    }

    @Test
    void negativeCountersAreRejected() {
        assertThatThrownBy(() -> new OpportunityProviderFetchResult(-1, 0, 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("recordsFetched cannot be negative");
        assertThatThrownBy(() -> new OpportunityProviderFetchResult(0, -1, 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("recordsSkipped cannot be negative");
        assertThatThrownBy(() -> new OpportunityProviderFetchResult(0, 0, -1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("recordsFailed cannot be negative");
    }
}

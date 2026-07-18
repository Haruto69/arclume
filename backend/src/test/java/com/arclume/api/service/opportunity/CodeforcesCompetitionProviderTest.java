package com.arclume.api.service.opportunity;

import com.arclume.api.client.CodeforcesContestClient;
import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.domain.CompetitionFormat;
import com.arclume.api.domain.CompetitionPhase;
import com.arclume.api.domain.OpportunityCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodeforcesCompetitionProviderTest {

    private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");

    private CodeforcesContestClient client;
    private OpportunityProviderProperties properties;
    private CodeforcesCompetitionProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(CodeforcesContestClient.class);
        properties = new OpportunityProviderProperties();
        provider = new CodeforcesCompetitionProvider(
                client,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void providerMetadataAndDefaultActivationAreStable() {
        assertThat(provider.providerKey()).isEqualTo("CODEFORCES");
        assertThat(provider.category()).isEqualTo(OpportunityCategory.COMPETITION);
        assertThat(CodeforcesCompetitionProvider.ORGANIZER).isEqualTo("Codeforces");
        assertThat(CodeforcesCompetitionProvider.ATTRIBUTION_LABEL).isEqualTo("Contest data from Codeforces");
        assertThat(provider.isEnabled()).isFalse();

        properties.getProviders().getCodeforces().setEnabled(true);
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    void normalizesValidContestWithCanonicalUrlUtcTimestampsAndNoProviderNarrativeContent() {
        CodeforcesContestClient.CodeforcesContest contest = contest(
                1234L,
                "Codeforces Round",
                "CF",
                "BEFORE",
                NOW.plusSeconds(3600).getEpochSecond(),
                7200L);
        contest.setWebsiteUrl("https://unsafe.example.test/not-canonical");
        contest.setDescription("This provider narrative must not be copied");
        contest.setPreparedBy("contest writer");
        contest.setKind("Official Round");
        contest.setDifficulty(3);
        contest.setCity("Warsaw");
        contest.setCountry("Poland");
        when(client.fetchContests()).thenReturn(List.of(contest));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(1);
        assertThat(result.recordsSkipped()).isZero();
        assertThat(result.recordsFailed()).isZero();
        assertThat(result.records()).singleElement().satisfies(record -> {
            NormalizedCompetitionOpportunity normalized = (NormalizedCompetitionOpportunity) record;
            assertThat(normalized.sourceId()).isEqualTo("1234");
            assertThat(normalized.sourceUrl()).isEqualTo("https://codeforces.com/contest/1234");
            assertThat(normalized.attributionLabel()).isEqualTo("Contest data from Codeforces");
            assertThat(normalized.title()).isEqualTo("Codeforces Round");
            assertThat(normalized.organizer()).isEqualTo("Codeforces");
            assertThat(normalized.competitionFormat()).isEqualTo(CompetitionFormat.CF);
            assertThat(normalized.phase()).isEqualTo(CompetitionPhase.BEFORE);
            assertThat(normalized.kind()).isEqualTo("Official Round");
            assertThat(normalized.difficulty()).isEqualTo(3);
            assertThat(normalized.city()).isEqualTo("Warsaw");
            assertThat(normalized.country()).isEqualTo("Poland");
            assertThat(normalized.startsAt()).isEqualTo(NOW.plusSeconds(3600));
            assertThat(normalized.endsAt()).isEqualTo(NOW.plusSeconds(10800));
            assertThat(normalized.durationSeconds()).isEqualTo(7200L);
            assertThat(normalized.active()).isTrue();
            assertThat(normalized.toString())
                    .doesNotContain("This provider narrative must not be copied", "unsafe.example", "contest writer");
        });
    }

    @Test
    void mapsSupportedFormatsPhasesAndActiveStates() {
        when(client.fetchContests()).thenReturn(List.of(
                contest(1L, "Before CF", "CF", "BEFORE", NOW.plusSeconds(100).getEpochSecond(), 3600L),
                contest(2L, "Coding IOI", "IOI", "CODING", NOW.minusSeconds(100).getEpochSecond(), 3600L),
                contest(3L, "Pending ICPC", "ICPC", "PENDING_SYSTEM_TEST", NOW.minusSeconds(200).getEpochSecond(), 3600L),
                contest(4L, "System Test", "CF", "SYSTEM_TEST", NOW.minusSeconds(300).getEpochSecond(), 3600L),
                contest(5L, "Finished", "CF", "FINISHED", NOW.minusSeconds(400).getEpochSecond(), 3600L)
        ));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.records()).hasSize(5);
        assertThat(result.records())
                .extracting(record -> ((NormalizedCompetitionOpportunity) record).competitionFormat())
                .containsExactly(
                        CompetitionFormat.CF,
                        CompetitionFormat.IOI,
                        CompetitionFormat.ICPC,
                        CompetitionFormat.CF,
                        CompetitionFormat.CF);
        assertThat(result.records())
                .extracting(record -> ((NormalizedCompetitionOpportunity) record).phase())
                .containsExactly(
                        CompetitionPhase.BEFORE,
                        CompetitionPhase.CODING,
                        CompetitionPhase.PENDING_SYSTEM_TEST,
                        CompetitionPhase.SYSTEM_TEST,
                        CompetitionPhase.FINISHED);
        assertThat(result.records())
                .extracting(record -> ((NormalizedCompetitionOpportunity) record).active())
                .containsExactly(true, true, false, false, false);
    }

    @Test
    void retentionUsesInjectedClockAndKeepsActiveAndRecentCompletedContestsOnly() {
        properties.getProviders().getCodeforces().setPastRetentionDays(14);
        when(client.fetchContests()).thenReturn(List.of(
                contest(1L, "Upcoming", "CF", "BEFORE", NOW.plusSeconds(86400).getEpochSecond(), 3600L),
                contest(2L, "Running", "CF", "CODING", NOW.minusSeconds(1800).getEpochSecond(), 7200L),
                contest(3L, "Recent Finished", "CF", "FINISHED", NOW.minusSeconds(13 * 86400L).getEpochSecond(), 3600L),
                contest(4L, "Old Finished", "CF", "FINISHED", NOW.minusSeconds(15 * 86400L).getEpochSecond(), 3600L)
        ));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(4);
        assertThat(result.recordsSkipped()).isEqualTo(1);
        assertThat(result.records())
                .extracting(NormalizedOpportunityRecord::sourceId)
                .containsExactly("1", "2", "3");
    }

    @Test
    void invalidRecordsAreSkippedWithoutIncrementingFailedCounter() {
        CodeforcesContestClient.CodeforcesContest overflow = contest(
                8L,
                "Overflow",
                "CF",
                "BEFORE",
                NOW.getEpochSecond(),
                Long.MAX_VALUE);
        when(client.fetchContests()).thenReturn(List.of(
                contest(null, "Missing ID", "CF", "BEFORE", NOW.getEpochSecond(), 3600L),
                contest(0L, "Nonpositive ID", "CF", "BEFORE", NOW.getEpochSecond(), 3600L),
                contest(3L, " ", "CF", "BEFORE", NOW.getEpochSecond(), 3600L),
                contest(4L, "Unknown Type", "UNKNOWN", "BEFORE", NOW.getEpochSecond(), 3600L),
                contest(5L, "Unknown Phase", "CF", "UNKNOWN", NOW.getEpochSecond(), 3600L),
                contest(6L, "Missing Start", "CF", "BEFORE", null, 3600L),
                contest(7L, "Nonpositive Duration", "CF", "BEFORE", NOW.getEpochSecond(), 0L),
                overflow,
                contest(9L, "Bad Difficulty", "CF", "BEFORE", NOW.getEpochSecond(), 3600L, 6),
                contest(10L, "x".repeat(251), "CF", "BEFORE", NOW.getEpochSecond(), 3600L)
        ));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(10);
        assertThat(result.recordsSkipped()).isEqualTo(10);
        assertThat(result.recordsFailed()).isZero();
        assertThat(result.records()).isEmpty();
    }

    @Test
    void dateTimeExceptionDuringEndCalculationSkipsRecordWithoutThrowing() {
        when(client.fetchContests()).thenReturn(List.of(contest(
                99L,
                "Max Instant",
                "CF",
                "BEFORE",
                Instant.MAX.getEpochSecond(),
                1L)));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(1);
        assertThat(result.recordsSkipped()).isEqualTo(1);
        assertThat(result.recordsFailed()).isZero();
        assertThat(result.records()).isEmpty();
    }

    @Test
    void nullContestEntryIsSkippedWhileValidContestIsNormalized() {
        List<CodeforcesContestClient.CodeforcesContest> contests = new ArrayList<>();
        contests.add(null);
        contests.add(contest(
                77L,
                "Valid Contest",
                "CF",
                "BEFORE",
                NOW.plusSeconds(3600).getEpochSecond(),
                3600L));
        when(client.fetchContests()).thenReturn(contests);

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(2);
        assertThat(result.recordsSkipped()).isEqualTo(1);
        assertThat(result.recordsFailed()).isZero();
        assertThat(result.records()).singleElement()
                .satisfies(record -> assertThat(record.sourceId()).isEqualTo("77"));
    }
    @Test
    void duplicateContestIdsKeepFirstDeterministicOccurrenceAndSkipLaterDuplicates() {
        when(client.fetchContests()).thenReturn(List.of(
                contest(42L, "First Title", "CF", "BEFORE", NOW.getEpochSecond(), 3600L),
                contest(42L, "Duplicate Title", "CF", "BEFORE", NOW.plusSeconds(10).getEpochSecond(), 3600L)
        ));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(2);
        assertThat(result.recordsSkipped()).isEqualTo(1);
        assertThat(result.records()).singleElement()
                .satisfies(record -> assertThat(((NormalizedCompetitionOpportunity) record).title())
                        .isEqualTo("First Title"));
    }

    @Test
    void clientFailurePropagatesAsProviderWideFailure() {
        when(client.fetchContests()).thenThrow(new OpportunityProviderException(
                CodeforcesContestClient.FAILURE_MESSAGE,
                null));

        assertThatThrownBy(provider::fetchOpportunities)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage(CodeforcesContestClient.FAILURE_MESSAGE);
    }

    private CodeforcesContestClient.CodeforcesContest contest(
            Long id,
            String name,
            String type,
            String phase,
            Long startTimeSeconds,
            Long durationSeconds) {
        return contest(id, name, type, phase, startTimeSeconds, durationSeconds, null);
    }

    private CodeforcesContestClient.CodeforcesContest contest(
            Long id,
            String name,
            String type,
            String phase,
            Long startTimeSeconds,
            Long durationSeconds,
            Integer difficulty) {
        CodeforcesContestClient.CodeforcesContest contest = new CodeforcesContestClient.CodeforcesContest();
        contest.setId(id);
        contest.setName(name);
        contest.setType(type);
        contest.setPhase(phase);
        contest.setStartTimeSeconds(startTimeSeconds);
        contest.setDurationSeconds(durationSeconds);
        contest.setDifficulty(difficulty);
        return contest;
    }
}

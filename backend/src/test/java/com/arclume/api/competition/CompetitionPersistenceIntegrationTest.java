package com.arclume.api.competition;

import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.Competition;
import com.arclume.api.domain.CompetitionFormat;
import com.arclume.api.domain.CompetitionPhase;
import com.arclume.api.repository.CompetitionRepository;
import com.arclume.api.service.opportunity.CompetitionOpportunityWriter;
import com.arclume.api.service.opportunity.NormalizedCompetitionOpportunity;
import com.arclume.api.service.opportunity.OpportunityPersistResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CompetitionPersistenceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CompetitionRepository competitionRepository;

    @Autowired
    private CompetitionOpportunityWriter competitionOpportunityWriter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        competitionRepository.deleteAll();
    }

    @Test
    void competitionMigrationIsRecordedWithColumnsIndexesAndConstraints() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '11' AND success = true",
                Integer.class);
        assertThat(count).isEqualTo(1);

        assertThat(columnsFor("competitions")).contains(
                "id",
                "title",
                "organizer",
                "competition_format",
                "phase",
                "kind",
                "difficulty",
                "city",
                "country",
                "starts_at",
                "ends_at",
                "duration_seconds",
                "external_url",
                "source_provider",
                "source_id",
                "attribution_label",
                "synced_at",
                "is_active",
                "created_at",
                "updated_at"
        );

        List<String> indexes = jdbcTemplate.queryForList(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'competitions'
                """,
                String.class);
        assertThat(indexes).contains(
                "uk_competitions_source",
                "idx_competitions_starts_at",
                "idx_competitions_active_starts_at",
                "idx_competitions_phase");

        List<String> constraints = jdbcTemplate.queryForList(
                """
                SELECT conname
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = 'public'
                  AND t.relname = 'competitions'
                """,
                String.class);
        assertThat(constraints).contains(
                "uk_competitions_source",
                "chk_competitions_duration_positive",
                "chk_competitions_ends_at",
                "chk_competitions_difficulty");
    }

    @Test
    void writerCreatesThenUpdatesBySourceIdentityWithoutDuplicatingRows() {
        Instant firstSync = Instant.parse("2026-07-18T12:00:00Z");
        Instant secondSync = Instant.parse("2026-07-18T13:00:00Z");
        NormalizedCompetitionOpportunity first = record(
                "9001",
                "First Title",
                CompetitionPhase.CODING,
                true,
                firstSync);
        NormalizedCompetitionOpportunity second = record(
                "9001",
                "Updated Title",
                CompetitionPhase.FINISHED,
                false,
                secondSync);

        OpportunityPersistResult created = competitionOpportunityWriter.upsert(" codeforces ", first, firstSync);
        OpportunityPersistResult updated = competitionOpportunityWriter.upsert("CODEFORCES", second, secondSync);

        assertThat(created).isEqualTo(OpportunityPersistResult.CREATED);
        assertThat(updated).isEqualTo(OpportunityPersistResult.UPDATED);
        assertThat(competitionRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getSourceProvider()).isEqualTo("CODEFORCES");
            assertThat(saved.getSourceId()).isEqualTo("9001");
            assertThat(saved.getTitle()).isEqualTo("Updated Title");
            assertThat(saved.getPhase()).isEqualTo(CompetitionPhase.FINISHED);
            assertThat(saved.getSyncedAt()).isEqualTo(secondSync);
            assertThat(saved.getActive()).isFalse();
        });
    }

    @Test
    void uniqueSourceDurationEndTimestampAndDifficultyConstraintsAreEnforced() {
        Competition saved = competition("CODEFORCES", "shared", 3600, 3);
        competitionRepository.saveAndFlush(saved);

        Competition duplicate = competition(" codeforces ", " shared ", 3600, 3);
        assertThatThrownBy(() -> competitionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertCompetition("bad-duration", 0, 3, false))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertCompetition("bad-difficulty", 3600, 6, false))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertCompetition("bad-range", 3600, 3, true))
                .isInstanceOf(DataAccessException.class);
    }

    private NormalizedCompetitionOpportunity record(
            String sourceId,
            String title,
            CompetitionPhase phase,
            boolean active,
            Instant syncedAt) {
        return new NormalizedCompetitionOpportunity(
                sourceId,
                "https://codeforces.com/contest/" + sourceId,
                "Contest data from Codeforces",
                title,
                "Codeforces",
                CompetitionFormat.CF,
                phase,
                "Official",
                2,
                "Warsaw",
                "Poland",
                syncedAt,
                syncedAt.plusSeconds(7200),
                7200,
                active
        );
    }

    private Competition competition(String sourceProvider, String sourceId, long durationSeconds, Integer difficulty) {
        Competition competition = new Competition();
        competition.setTitle("Stored Contest");
        competition.setOrganizer("Codeforces");
        competition.setCompetitionFormat(CompetitionFormat.CF);
        competition.setPhase(CompetitionPhase.BEFORE);
        competition.setKind("Official");
        competition.setDifficulty(difficulty);
        competition.setCity("Warsaw");
        competition.setCountry("Poland");
        competition.setStartsAt(Instant.parse("2026-07-18T12:00:00Z"));
        competition.setEndsAt(Instant.parse("2026-07-18T13:00:00Z"));
        competition.setDurationSeconds(durationSeconds);
        competition.setExternalUrl("https://codeforces.com/contest/" + sourceId.trim());
        competition.setSourceProvider(sourceProvider);
        competition.setSourceId(sourceId);
        competition.setAttributionLabel("Contest data from Codeforces");
        competition.setActive(true);
        return competition;
    }

    private void insertCompetition(String sourceId, long durationSeconds, Integer difficulty, boolean endBeforeStart) {
        Instant startsAt = Instant.parse("2026-07-18T12:00:00Z");
        Instant endsAt = endBeforeStart ? startsAt.minusSeconds(1) : startsAt.plusSeconds(3600);
        jdbcTemplate.update("""
                INSERT INTO competitions (
                    id, title, organizer, competition_format, phase, starts_at, ends_at, duration_seconds,
                    external_url, source_provider, source_id, difficulty, is_active, created_at, updated_at
                ) VALUES (?, 'Contest', 'Codeforces', 'CF', 'BEFORE', ?, ?, ?,
                    ?, 'CODEFORCES', ?, ?, true, NOW(), NOW())
                """,
                UUID.randomUUID(),
                startsAt,
                endsAt,
                durationSeconds,
                "https://codeforces.com/contest/" + sourceId,
                sourceId,
                difficulty);
    }

    private List<String> columnsFor(String tableName) {
        return jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                """,
                String.class,
                tableName);
    }
}

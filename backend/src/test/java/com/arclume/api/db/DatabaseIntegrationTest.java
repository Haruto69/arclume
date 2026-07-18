package com.arclume.api.db;

import com.arclume.api.client.RemotiveJobClient;
import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.OpportunityCategory;
import com.arclume.api.domain.OpportunitySyncCounters;
import com.arclume.api.domain.OpportunitySyncRun;
import com.arclume.api.domain.OpportunitySyncStatus;
import com.arclume.api.repository.OpportunitySyncRunRepository;
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
class DatabaseIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OpportunitySyncRunRepository opportunitySyncRunRepository;

    @Autowired
    private RemotiveJobClient remotiveJobClient;

    @Test
    void contextLoads_AndDatabaseIsConnected() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(result).isEqualTo(1);
        assertThat(remotiveJobClient).isNotNull();
    }

    @Test
    void flywayMigration_V1_RecordedSuccessfully() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void hardenedAuthenticationMigrationIsRecordedWithLifecycleIndexesAndSafeDefaults() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '9' AND success = true",
                Integer.class);
        assertThat(count).isEqualTo(1);

        String emailVerifiedDefault = jdbcTemplate.queryForObject(
                """
                SELECT column_default
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'users'
                  AND column_name = 'email_verified'
                """,
                String.class);
        assertThat(emailVerifiedDefault).containsIgnoringCase("false");

        List<String> indexes = jdbcTemplate.queryForList(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename IN ('email_verification_tokens', 'user_sessions', 'recovery_codes')
                """,
                String.class);
        assertThat(indexes).contains(
                "idx_email_verification_tokens_consumed_at",
                "idx_user_sessions_expires_at",
                "idx_user_sessions_revoked_at",
                "idx_recovery_codes_used_at",
                "uk_recovery_codes_user_hash");
    }

    @Test
    void opportunityProviderArchitectureMigrationIsRecordedWithMetadataColumnsAndIndexes() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '10' AND success = true",
                Integer.class);
        assertThat(count).isEqualTo(1);

        List<String> syncRunColumns = columnsFor("opportunity_sync_runs");
        assertThat(syncRunColumns).contains(
                "id",
                "provider_key",
                "category",
                "status",
                "started_at",
                "finished_at",
                "records_fetched",
                "records_created",
                "records_updated",
                "records_skipped",
                "records_failed",
                "records_deactivated",
                "sync_error",
                "created_at",
                "updated_at"
        );
        assertThat(columnsFor("jobs")).contains("attribution_label");
        assertThat(columnsFor("hackathons")).contains("attribution_label", "synced_at");
        assertThat(columnsFor("student_programs")).contains("attribution_label", "synced_at");

        List<String> indexes = jdbcTemplate.queryForList(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename IN ('opportunity_sync_runs', 'jobs', 'hackathons', 'student_programs')
                """,
                String.class);
        assertThat(indexes).contains(
                "idx_opportunity_sync_runs_provider_started_at",
                "idx_opportunity_sync_runs_status_started_at",
                "idx_jobs_source_provider",
                "idx_hackathons_source_synced_at",
                "idx_student_programs_source_synced_at"
        );
    }

    @Test
    void jobSourceExternalUniquenessConstraintIsPresentFromV4() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '4' AND success = true",
                Integer.class);
        assertThat(count).isEqualTo(1);

        List<String> constraints = jdbcTemplate.queryForList(
                """
                SELECT c.conname
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = 'public'
                  AND t.relname = 'jobs'
                  AND c.contype = 'u'
                """,
                String.class);
        assertThat(constraints).contains("uk_job_source_external");

        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT a.attname
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                JOIN unnest(c.conkey) WITH ORDINALITY AS cols(attnum, ordinality) ON true
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = cols.attnum
                WHERE n.nspname = 'public'
                  AND t.relname = 'jobs'
                  AND c.conname = 'uk_job_source_external'
                ORDER BY cols.ordinality
                """,
                String.class);
        assertThat(columns).containsExactly("source_provider", "external_id");
    }

    @Test
    void syncRunEntityPersistsAndSanitizesBoundedErrors() {
        opportunitySyncRunRepository.deleteAll();
        OpportunitySyncRun run = OpportunitySyncRun.start(" remotive ", OpportunityCategory.JOB, Instant.now());
        run = opportunitySyncRunRepository.saveAndFlush(run);

        run.complete(
                OpportunitySyncStatus.FAILED,
                new OpportunitySyncCounters(0, 0, 0, 0, 1, 0),
                Instant.now(),
                "Authorization: Bearer actual-secret-token\n"
                        + "Bearer standalone-secret-token\t"
                        + "api_key=api-secret apiKey=camel-secret token=token-secret "
                        + "access_token=access-secret password=password-secret "
                        + "Cookie: session=cookie-secret Set-Cookie: refresh=set-cookie-secret "
                        + "https://provider.example/jobs?api_key=query-secret&access_token=query-token "
                        + "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signaturepart "
                        + "x".repeat(1200)
        );
        run = opportunitySyncRunRepository.saveAndFlush(run);

        assertThat(run.getProviderKey()).isEqualTo("REMOTIVE");
        assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.FAILED);
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(run.getSyncError()).doesNotContain(
                "actual-secret-token",
                "standalone-secret-token",
                "api-secret",
                "camel-secret",
                "token-secret",
                "access-secret",
                "password-secret",
                "cookie-secret",
                "set-cookie-secret",
                "query-secret",
                "query-token",
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signaturepart"
        );
        assertThat(run.getSyncError()).doesNotContain("\n", "\t");
        assertThat(run.getSyncError()).hasSizeLessThanOrEqualTo(1000);
        assertThat(opportunitySyncRunRepository.findTop20ByProviderKeyOrderByStartedAtDesc("REMOTIVE"))
                .extracting(OpportunitySyncRun::getId)
                .contains(run.getId());
    }

    @Test
    void syncRunCounterChecksAreEnforcedByDatabase() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO opportunity_sync_runs (
                    id, created_at, updated_at, provider_key, category, status,
                    started_at, finished_at, records_fetched
                ) VALUES (?, NOW(), NOW(), 'REMOTIVE', 'JOB', 'SUCCEEDED', NOW(), NOW(), -1)
                """, UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
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

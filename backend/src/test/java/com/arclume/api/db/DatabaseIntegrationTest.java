package com.arclume.api.db;

import com.arclume.api.config.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DatabaseIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads_AndDatabaseIsConnected() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(result).isEqualTo(1);
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
}

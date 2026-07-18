CREATE TABLE opportunity_sync_runs (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    provider_key VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    records_fetched INTEGER NOT NULL DEFAULT 0,
    records_created INTEGER NOT NULL DEFAULT 0,
    records_updated INTEGER NOT NULL DEFAULT 0,
    records_skipped INTEGER NOT NULL DEFAULT 0,
    records_failed INTEGER NOT NULL DEFAULT 0,
    records_deactivated INTEGER NOT NULL DEFAULT 0,
    sync_error VARCHAR(1000),
    CONSTRAINT chk_opportunity_sync_runs_provider_key
        CHECK (
            provider_key = UPPER(TRIM(provider_key))
            AND provider_key ~ '^[A-Z0-9][A-Z0-9_-]{0,99}$'
        ),
    CONSTRAINT chk_opportunity_sync_runs_category
        CHECK (category IN ('JOB', 'HACKATHON', 'COMPETITION', 'EVENT', 'STUDENT_PROGRAM')),
    CONSTRAINT chk_opportunity_sync_runs_status
        CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_opportunity_sync_runs_finished_at
        CHECK (
            (status = 'RUNNING' AND finished_at IS NULL)
            OR (status <> 'RUNNING' AND finished_at IS NOT NULL)
        ),
    CONSTRAINT chk_opportunity_sync_runs_non_negative_counters
        CHECK (
            records_fetched >= 0
            AND records_created >= 0
            AND records_updated >= 0
            AND records_skipped >= 0
            AND records_failed >= 0
            AND records_deactivated >= 0
        )
);

CREATE INDEX idx_opportunity_sync_runs_provider_started_at
    ON opportunity_sync_runs(provider_key, started_at DESC);

CREATE INDEX idx_opportunity_sync_runs_status_started_at
    ON opportunity_sync_runs(status, started_at DESC);

ALTER TABLE jobs
    ADD COLUMN attribution_label VARCHAR(120);

CREATE INDEX idx_jobs_source_provider
    ON jobs(source_provider);

ALTER TABLE hackathons
    ADD COLUMN attribution_label VARCHAR(120),
    ADD COLUMN synced_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_hackathons_source_synced_at
    ON hackathons(source_provider, synced_at DESC);

ALTER TABLE student_programs
    ADD COLUMN attribution_label VARCHAR(120),
    ADD COLUMN synced_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_student_programs_source_synced_at
    ON student_programs(source_provider, synced_at DESC);

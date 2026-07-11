ALTER TABLE jobs ADD COLUMN external_id VARCHAR(100);
ALTER TABLE jobs ADD COLUMN source_provider VARCHAR(50);
ALTER TABLE jobs ADD COLUMN salary_range VARCHAR(255);
ALTER TABLE jobs ADD COLUMN work_mode VARCHAR(50) DEFAULT 'REMOTE';
ALTER TABLE jobs ADD COLUMN posted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE jobs ADD COLUMN synced_at TIMESTAMP WITH TIME ZONE;

-- Unique constraint for source_provider and external_id (allows multiple NULLs in Postgres)
ALTER TABLE jobs ADD CONSTRAINT uk_job_source_external UNIQUE (source_provider, external_id);

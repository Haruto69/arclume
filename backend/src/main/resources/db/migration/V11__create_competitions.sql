CREATE TABLE competitions (
    id UUID PRIMARY KEY,
    title VARCHAR(250) NOT NULL,
    organizer VARCHAR(200) NOT NULL,
    competition_format VARCHAR(30) NOT NULL,
    phase VARCHAR(40) NOT NULL,
    kind VARCHAR(200),
    difficulty INTEGER,
    city VARCHAR(150),
    country VARCHAR(150),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_seconds BIGINT NOT NULL,
    external_url VARCHAR(1000) NOT NULL,
    source_provider VARCHAR(100) NOT NULL,
    source_id VARCHAR(200) NOT NULL,
    attribution_label VARCHAR(120),
    synced_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_competitions_source UNIQUE (source_provider, source_id),
    CONSTRAINT chk_competitions_duration_positive CHECK (duration_seconds > 0),
    CONSTRAINT chk_competitions_ends_at CHECK (ends_at >= starts_at),
    CONSTRAINT chk_competitions_difficulty CHECK (difficulty IS NULL OR difficulty BETWEEN 1 AND 5)
);

CREATE INDEX idx_competitions_starts_at
    ON competitions(starts_at);

CREATE INDEX idx_competitions_active_starts_at
    ON competitions(is_active, starts_at);

CREATE INDEX idx_competitions_phase
    ON competitions(phase);

CREATE TABLE hackathons (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    title VARCHAR(250) NOT NULL,
    organizer VARCHAR(200) NOT NULL,
    organizer_type VARCHAR(50) NOT NULL,
    city VARCHAR(150),
    region VARCHAR(150),
    country VARCHAR(150),
    mode VARCHAR(50) NOT NULL,
    prize_pool_amount NUMERIC(19, 2),
    prize_pool_currency VARCHAR(3),
    registration_deadline DATE,
    start_date DATE,
    end_date DATE,
    external_url VARCHAR(1000),
    source_provider VARCHAR(100) NOT NULL,
    source_id VARCHAR(200),
    description TEXT,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_hackathon_source UNIQUE (source_provider, source_id),
    CONSTRAINT chk_hackathon_mode CHECK (mode IN ('ONLINE', 'IN_PERSON', 'HYBRID')),
    CONSTRAINT chk_hackathon_organizer_type CHECK (organizer_type IN ('COLLEGE', 'COMPANY', 'COMMUNITY', 'GOVERNMENT', 'OTHER')),
    CONSTRAINT chk_hackathon_prize_pool CHECK (prize_pool_amount IS NULL OR prize_pool_amount >= 0),
    CONSTRAINT chk_hackathon_dates CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date),
    CONSTRAINT chk_hackathon_tags_array CHECK (jsonb_typeof(tags) = 'array')
);

CREATE INDEX idx_hackathons_active ON hackathons(is_active);
CREATE INDEX idx_hackathons_city ON hackathons(city);
CREATE INDEX idx_hackathons_region ON hackathons(region);
CREATE INDEX idx_hackathons_country ON hackathons(country);
CREATE INDEX idx_hackathons_mode ON hackathons(mode);
CREATE INDEX idx_hackathons_organizer_type ON hackathons(organizer_type);
CREATE INDEX idx_hackathons_start_date ON hackathons(start_date);
CREATE INDEX idx_hackathons_active_start_date ON hackathons(is_active, start_date);
CREATE INDEX idx_hackathons_source_provider ON hackathons(source_provider);


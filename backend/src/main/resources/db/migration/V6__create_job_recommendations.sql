CREATE TABLE job_recommendations (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id UUID NOT NULL,
    job_id UUID NOT NULL,
    match_score INTEGER NOT NULL,
    matched_skills JSONB NOT NULL DEFAULT '[]'::jsonb,
    missing_skills JSONB NOT NULL DEFAULT '[]'::jsonb,
    explanation TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_job_recommendation_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_recommendation_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT uk_job_recommendation_user_job UNIQUE (user_id, job_id),
    CONSTRAINT chk_job_recommendation_score CHECK (match_score >= 0 AND match_score <= 100),
    CONSTRAINT chk_job_recommendation_status CHECK (status IN ('ACTIVE', 'DISMISSED', 'SAVED', 'EXPIRED')),
    CONSTRAINT chk_job_recommendation_matched_skills_array CHECK (jsonb_typeof(matched_skills) = 'array'),
    CONSTRAINT chk_job_recommendation_missing_skills_array CHECK (jsonb_typeof(missing_skills) = 'array')
);

CREATE INDEX idx_job_recommendations_user ON job_recommendations(user_id);
CREATE INDEX idx_job_recommendations_user_status ON job_recommendations(user_id, status);
CREATE INDEX idx_job_recommendations_user_score ON job_recommendations(user_id, match_score DESC);
CREATE INDEX idx_job_recommendations_user_generated_at ON job_recommendations(user_id, generated_at DESC);

CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    headline VARCHAR(160),
    bio TEXT,
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    education_level VARCHAR(50),
    institution VARCHAR(200),
    field_of_study VARCHAR(200),
    graduation_year INTEGER,
    years_experience INTEGER,
    "current_role" VARCHAR(160),
    desired_roles JSONB NOT NULL DEFAULT '[]'::jsonb,
    preferred_locations JSONB NOT NULL DEFAULT '[]'::jsonb,
    preferred_work_modes JSONB NOT NULL DEFAULT '[]'::jsonb,
    preferred_employment_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    open_to_relocation BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_user_profiles_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_profiles_user UNIQUE (user_id),
    CONSTRAINT chk_user_profiles_graduation_year
        CHECK (graduation_year IS NULL OR graduation_year BETWEEN 1950 AND 2100),
    CONSTRAINT chk_user_profiles_years_experience
        CHECK (years_experience IS NULL OR years_experience BETWEEN 0 AND 60),
    CONSTRAINT chk_user_profiles_desired_roles_array
        CHECK (jsonb_typeof(desired_roles) = 'array'),
    CONSTRAINT chk_user_profiles_preferred_locations_array
        CHECK (jsonb_typeof(preferred_locations) = 'array'),
    CONSTRAINT chk_user_profiles_preferred_work_modes_array
        CHECK (jsonb_typeof(preferred_work_modes) = 'array'),
    CONSTRAINT chk_user_profiles_preferred_employment_types_array
        CHECK (jsonb_typeof(preferred_employment_types) = 'array'),
    CONSTRAINT chk_user_profiles_education_level
        CHECK (
            education_level IS NULL
            OR education_level IN ('HIGH_SCHOOL', 'DIPLOMA', 'BACHELORS', 'MASTERS', 'DOCTORATE', 'OTHER')
        ),
    CONSTRAINT chk_user_profiles_preferred_work_modes_values
        CHECK (
            NOT jsonb_path_exists(
                preferred_work_modes,
                '$[*] ? (@ != "REMOTE" && @ != "HYBRID" && @ != "ON_SITE")'
            )
        ),
    CONSTRAINT chk_user_profiles_preferred_employment_types_values
        CHECK (
            NOT jsonb_path_exists(
                preferred_employment_types,
                '$[*] ? (@ != "FULL_TIME" && @ != "PART_TIME" && @ != "CONTRACT" && @ != "INTERNSHIP")'
            )
        )
);

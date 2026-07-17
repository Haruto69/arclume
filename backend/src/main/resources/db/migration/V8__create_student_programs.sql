CREATE TABLE student_programs (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    title VARCHAR(250) NOT NULL,
    company VARCHAR(200) NOT NULL,
    program_type VARCHAR(50) NOT NULL,
    mode VARCHAR(50) NOT NULL,
    region VARCHAR(150),
    country VARCHAR(150),
    eligibility TEXT,
    benefit_summary TEXT,
    application_deadline DATE,
    start_date DATE,
    end_date DATE,
    always_open BOOLEAN NOT NULL DEFAULT FALSE,
    external_url VARCHAR(1000) NOT NULL,
    source_provider VARCHAR(100) NOT NULL,
    source_id VARCHAR(200),
    description TEXT,
    benefit_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_verified_at DATE,
    CONSTRAINT chk_student_program_type CHECK (program_type IN (
        'DEVELOPER_PACK', 'STUDENT_AMBASSADOR', 'CLOUD_CREDITS', 'CERTIFICATION',
        'CHALLENGE', 'EVENT_SERIES', 'OPEN_SOURCE', 'LEARNING', 'DESIGN',
        'COMMUNITY', 'CAREER', 'OTHER'
    )),
    CONSTRAINT chk_student_program_mode CHECK (mode IN ('ONLINE', 'IN_PERSON', 'HYBRID', 'UNKNOWN')),
    CONSTRAINT chk_student_program_dates CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date),
    CONSTRAINT chk_student_program_benefit_types_array CHECK (jsonb_typeof(benefit_types) = 'array'),
    CONSTRAINT chk_student_program_tags_array CHECK (jsonb_typeof(tags) = 'array')
);

CREATE INDEX idx_student_programs_active ON student_programs(is_active);
CREATE INDEX idx_student_programs_company ON student_programs(company);
CREATE INDEX idx_student_programs_program_type ON student_programs(program_type);
CREATE INDEX idx_student_programs_mode ON student_programs(mode);
CREATE INDEX idx_student_programs_country ON student_programs(country);
CREATE INDEX idx_student_programs_region ON student_programs(region);
CREATE INDEX idx_student_programs_always_open ON student_programs(always_open);
CREATE INDEX idx_student_programs_application_deadline ON student_programs(application_deadline);
CREATE UNIQUE INDEX uk_student_programs_source
    ON student_programs(source_provider, source_id)
    WHERE source_id IS NOT NULL;

INSERT INTO student_programs (
    id, created_at, updated_at, title, company, program_type, mode, region, country,
    eligibility, benefit_summary, application_deadline, start_date, end_date,
    always_open, external_url, source_provider, source_id, description,
    benefit_types, tags, is_active, last_verified_at
) VALUES
(
    '12000000-0000-0000-0000-000000000001', NOW(), NOW(),
    'GitHub Student Developer Pack', 'GitHub', 'DEVELOPER_PACK', 'ONLINE', NULL, NULL,
    'Verified students who meet GitHub Education eligibility requirements. Eligibility and included offers can change.',
    'A changing collection of developer tools and learning offers may be available after student verification.',
    NULL, NULL, NULL, TRUE, 'https://education.github.com/pack', 'OFFICIAL',
    'github-student-developer-pack',
    'GitHub Education curates partner resources for eligible students. Always verify current eligibility, offer terms, and availability on the official page.',
    '["FREE_TOOLS", "CLOUD_CREDITS", "TRAINING"]'::jsonb,
    '["developer tools", "student benefit", "education"]'::jsonb, TRUE, DATE '2026-07-17'
),
(
    '12000000-0000-0000-0000-000000000002', NOW(), NOW(),
    'AWS Educate', 'AWS', 'LEARNING', 'ONLINE', NULL, NULL,
    'Available to individual learners subject to AWS Educate account and service requirements.',
    'Free, self-paced cloud learning resources, hands-on practice, and digital badges may be available.',
    NULL, NULL, NULL, TRUE, 'https://aws.amazon.com/education/awseducate/', 'OFFICIAL',
    'aws-educate',
    'AWS Educate provides cloud learning resources for learners. Course access, badges, labs, and account requirements can change, so verify them on the official page.',
    '["TRAINING", "BADGE"]'::jsonb,
    '["cloud", "learning", "student benefit"]'::jsonb, TRUE, DATE '2026-07-17'
),
(
    '12000000-0000-0000-0000-000000000003', NOW(), NOW(),
    'Microsoft Learn Student Hub', 'Microsoft', 'LEARNING', 'ONLINE', NULL, NULL,
    'Students and learners interested in building technical skills; individual resource requirements may vary.',
    'Guided learning resources and student-focused technical content are available through Microsoft Learn.',
    NULL, NULL, NULL, TRUE, 'https://learn.microsoft.com/en-us/training/student-hub/', 'OFFICIAL',
    'microsoft-learn-student-hub',
    'The Microsoft Learn Student Hub organizes learning paths and resources for students. Verify current content and any separate program requirements on the official page.',
    '["TRAINING"]'::jsonb,
    '["Microsoft Learn", "technical skills", "learning"]'::jsonb, TRUE, DATE '2026-07-17'
),
(
    '12000000-0000-0000-0000-000000000004', NOW(), NOW(),
    'MongoDB Student Pack', 'MongoDB', 'CLOUD_CREDITS', 'ONLINE', NULL, NULL,
    'Students verified through the GitHub Student Developer Pack. Separate MongoDB account and offer terms may apply.',
    'MongoDB Atlas credits and certification access may be available; values, expiration, and requirements can change.',
    NULL, NULL, NULL, TRUE, 'https://www.mongodb.com/students', 'OFFICIAL',
    'mongodb-student-pack',
    'MongoDB provides student offers through its official student page. Verify current credit, certification, payment-method, and expiration terms before enrolling.',
    '["CLOUD_CREDITS", "CERTIFICATE", "TRAINING"]'::jsonb,
    '["database", "cloud", "student benefit"]'::jsonb, TRUE, DATE '2026-07-17'
),
(
    '12000000-0000-0000-0000-000000000005', NOW(), NOW(),
    'JetBrains Student Pack', 'JetBrains', 'DEVELOPER_PACK', 'ONLINE', NULL, NULL,
    'Verified students and teachers who meet JetBrains educational license requirements. Educational-use restrictions apply.',
    'Eligible learners may receive educational access to JetBrains development tools.',
    NULL, NULL, NULL, TRUE, 'https://www.jetbrains.com/community/education/#students', 'OFFICIAL',
    'jetbrains-student-pack',
    'JetBrains offers educational licenses for eligible students and teachers. Verify supported products, verification methods, renewal, and non-commercial-use terms on the official page.',
    '["FREE_TOOLS"]'::jsonb,
    '["developer tools", "IDE", "student license"]'::jsonb, TRUE, DATE '2026-07-17'
),
(
    '12000000-0000-0000-0000-000000000006', NOW(), NOW(),
    'Figma for Education', 'Figma', 'DESIGN', 'ONLINE', NULL, NULL,
    'Eligible students and educators who complete Figma education verification. Access duration and verification methods can vary.',
    'Education plan access to Figma tools may be available after eligibility verification.',
    NULL, NULL, NULL, TRUE, 'https://www.figma.com/education/', 'OFFICIAL',
    'figma-for-education',
    'Figma provides an education offering for eligible students and educators. Verify current plan features, access period, and re-verification requirements on the official page.',
    '["FREE_TOOLS"]'::jsonb,
    '["design", "collaboration", "student benefit"]'::jsonb, TRUE, DATE '2026-07-17'
),
(
    '12000000-0000-0000-0000-000000000007', NOW(), NOW(),
    'Notion for Education', 'Notion', 'OTHER', 'ONLINE', NULL, NULL,
    'Eligible college and university students and educators using a qualifying school email. Workspace and region requirements may apply.',
    'Eligible individual users may receive education access to selected Notion plan features.',
    NULL, NULL, NULL, TRUE, 'https://www.notion.com/help/notion-for-education', 'OFFICIAL',
    'notion-for-education',
    'Notion offers an education plan for eligible students and educators. Verify current plan limits, workspace rules, email eligibility, and regional availability on the official page.',
    '["FREE_TOOLS"]'::jsonb,
    '["productivity", "education", "student benefit"]'::jsonb, TRUE, DATE '2026-07-17'
),
(
    '12000000-0000-0000-0000-000000000008', NOW(), NOW(),
    'Google Developer Groups on Campus', 'Google', 'COMMUNITY', 'UNKNOWN', NULL, NULL,
    'Students interested in technology; chapter membership and local event availability vary by campus and region.',
    'Local communities may offer technical learning, events, and networking opportunities.',
    NULL, NULL, NULL, FALSE, 'https://developers.google.com/community', 'OFFICIAL',
    'google-developer-groups-on-campus',
    'Google Developer Groups on Campus are student communities whose activities vary by chapter. Use the official community directory to verify local availability and participation details.',
    '["NETWORKING", "TRAINING"]'::jsonb,
    '["developer community", "campus", "events"]'::jsonb, TRUE, DATE '2026-07-17'
);

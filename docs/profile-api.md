# Profile API

Phase 16 adds an authenticated user profile and career-preference store. The profile data is owned by the signed-in user and is intended as storage for later personalization work; recommendation scoring, ranking, filtering, providers, AI, RAG, and embeddings are unchanged.

## Stored Fields

`user_profiles` stores one row per user with public-facing and preference fields:

- Profile summary: `headline`, `bio`, `current_role`.
- Location: `city`, `state`, `country`.
- Education and experience: `education_level`, `institution`, `field_of_study`, `graduation_year`, `years_experience`.
- Preferences: `desired_roles`, `preferred_locations`, `preferred_work_modes`, `preferred_employment_types`, `open_to_relocation`.

Identity fields returned by the API come from `users`: `userId`, `email`, `firstName`, and `lastName`. Email, role, password, password hash, verification state, TOTP state, timestamps, and session data are not editable through the profile API. Unknown request fields, including protected identity and security fields, are rejected instead of being silently ignored.

## Storage

The table is created by `V12__create_user_profiles.sql`. `user_id` is nonnull, unique, and references `users(id)` with `ON DELETE CASCADE`, so account deletion removes the profile automatically. Preference lists are stored as nonnull PostgreSQL `JSONB` arrays with `[]` defaults. Database constraints enforce valid graduation year, years of experience, education-level enum names, work-mode enum names, employment-type enum names, and JSON array shape.

## Endpoints

Both endpoints are under `/api/v1/profile` and require the existing authenticated session cookie.

- `GET /api/v1/profile` returns the authenticated user's profile aggregate. If no profile row exists, it returns identity fields plus null profile scalars, empty preference arrays, and `openToRelocation: false`; it does not create a row.
- `PUT /api/v1/profile` creates or updates the authenticated user's single profile row and also updates only `firstName` and `lastName` on `users`.

Unsafe requests use the existing CSRF flow: the frontend calls `GET /api/v1/auth/csrf`, reads the non-HttpOnly `XSRF-TOKEN` cookie, and sends it as `X-XSRF-TOKEN`. The HttpOnly session cookie is never read by frontend code.

## Validation And Normalization

Required scalar limits are enforced by Bean Validation and service normalization: `firstName` and `lastName` are required and max 100 characters. Optional fields are trimmed, blank optional strings become null, and max lengths are: headline/current role 160, bio 2000, city/state/country 100, institution/field of study 200. `graduationYear` must be 1950-2100 when present, and `yearsExperience` must be 0-60 when present.

`desiredRoles` and `preferredLocations` accept at most 10 entries, each up to 160 characters. Null entries, blank entries, and overlong entries are rejected. Values are trimmed, de-duplicated case-insensitively, and returned in first-seen order while preserving the first spelling entered by the user.

Enum preference arrays reject null or unknown values, remove duplicates, and preserve request order. Supported education levels are `HIGH_SCHOOL`, `DIPLOMA`, `BACHELORS`, `MASTERS`, `DOCTORATE`, and `OTHER`. Supported work modes are `REMOTE`, `HYBRID`, and `ON_SITE`. Supported profile employment types are `FULL_TIME`, `PART_TIME`, `CONTRACT`, and `INTERNSHIP`; job-only values outside the backend profile enum are not accepted.

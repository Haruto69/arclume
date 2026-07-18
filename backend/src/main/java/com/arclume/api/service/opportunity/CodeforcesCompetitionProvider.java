package com.arclume.api.service.opportunity;

import com.arclume.api.client.CodeforcesContestClient;
import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.domain.CompetitionFormat;
import com.arclume.api.domain.CompetitionPhase;
import com.arclume.api.domain.OpportunityCategory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class CodeforcesCompetitionProvider implements OpportunityProvider {

    public static final String PROVIDER_KEY = "CODEFORCES";
    public static final String ORGANIZER = "Codeforces";
    public static final String ATTRIBUTION_LABEL = "Contest data from Codeforces";

    private static final String CANONICAL_CONTEST_URL_PREFIX = "https://codeforces.com/contest/";

    private final CodeforcesContestClient codeforcesContestClient;
    private final OpportunityProviderProperties properties;
    private final Clock clock;

    public CodeforcesCompetitionProvider(
            CodeforcesContestClient codeforcesContestClient,
            OpportunityProviderProperties properties,
            Clock clock) {
        this.codeforcesContestClient = codeforcesContestClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }

    @Override
    public OpportunityCategory category() {
        return OpportunityCategory.COMPETITION;
    }

    @Override
    public boolean isEnabled() {
        return properties.getProviders().getCodeforces().isEnabled();
    }

    @Override
    public OpportunityProviderFetchResult fetchOpportunities() {
        List<CodeforcesContestClient.CodeforcesContest> contests = codeforcesContestClient.fetchContests();
        List<NormalizedOpportunityRecord> records = new ArrayList<>();
        Set<String> seenSourceIds = new HashSet<>();
        int skipped = 0;

        for (CodeforcesContestClient.CodeforcesContest contest : contests) {
            NormalizedCompetitionOpportunity normalized = normalize(contest);
            if (normalized == null || !seenSourceIds.add(normalized.sourceId())) {
                skipped++;
                continue;
            }
            records.add(normalized);
        }

        return new OpportunityProviderFetchResult(contests.size(), skipped, records);
    }

    private NormalizedCompetitionOpportunity normalize(CodeforcesContestClient.CodeforcesContest contest) {
        if (contest == null || contest.getId() == null || contest.getId() <= 0) {
            return null;
        }

        String sourceId = String.valueOf(contest.getId());
        String title = normalizeRequired(contest.getName(), 250);
        CompetitionFormat format = mapFormat(contest.getType());
        CompetitionPhase phase = mapPhase(contest.getPhase());
        Instant startsAt = toInstant(contest.getStartTimeSeconds());
        Long durationSeconds = contest.getDurationSeconds();
        if (title == null || format == null || phase == null || startsAt == null
                || durationSeconds == null || durationSeconds <= 0) {
            return null;
        }

        Instant endsAt;
        try {
            endsAt = startsAt.plusSeconds(durationSeconds);
        } catch (DateTimeException | ArithmeticException e) {
            return null;
        }
        if (endsAt.isBefore(startsAt) || !isRetained(phase, startsAt)) {
            return null;
        }

        Integer difficulty = contest.getDifficulty();
        if (difficulty != null && (difficulty < 1 || difficulty > 5)) {
            return null;
        }

        String kind = normalizeOptional(contest.getKind(), 200);
        String city = normalizeOptional(contest.getCity(), 150);
        String country = normalizeOptional(contest.getCountry(), 150);
        if (hasInvalidBoundedValue(contest.getKind(), kind)
                || hasInvalidBoundedValue(contest.getCity(), city)
                || hasInvalidBoundedValue(contest.getCountry(), country)) {
            return null;
        }

        try {
            return new NormalizedCompetitionOpportunity(
                    sourceId,
                    CANONICAL_CONTEST_URL_PREFIX + sourceId,
                    ATTRIBUTION_LABEL,
                    title,
                    ORGANIZER,
                    format,
                    phase,
                    kind,
                    difficulty,
                    city,
                    country,
                    startsAt,
                    endsAt,
                    durationSeconds,
                    isActive(phase)
            );
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isRetained(CompetitionPhase phase, Instant startsAt) {
        if (isActive(phase)) {
            return true;
        }
        Instant cutoff = clock.instant().minus(Duration.ofDays(
                properties.getProviders().getCodeforces().getPastRetentionDays()));
        return !startsAt.isBefore(cutoff);
    }

    private boolean isActive(CompetitionPhase phase) {
        return phase == CompetitionPhase.BEFORE || phase == CompetitionPhase.CODING;
    }

    private CompetitionFormat mapFormat(String type) {
        String normalized = normalizeOptional(type, 30);
        if (normalized == null) {
            return null;
        }
        try {
            return CompetitionFormat.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private CompetitionPhase mapPhase(String phase) {
        String normalized = normalizeOptional(phase, 40);
        if (normalized == null) {
            return null;
        }
        try {
            return CompetitionPhase.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Instant toInstant(Long epochSeconds) {
        if (epochSeconds == null) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(epochSeconds);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private String normalizeRequired(String value, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        return hasInvalidBoundedValue(value, normalized) ? null : normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : null;
    }

    private boolean hasInvalidBoundedValue(String raw, String normalized) {
        return raw != null && !raw.trim().isEmpty() && normalized == null;
    }
}

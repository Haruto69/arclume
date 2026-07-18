package com.arclume.api.repository;

import com.arclume.api.domain.Competition;
import com.arclume.api.domain.CompetitionFormat;
import com.arclume.api.domain.CompetitionPhase;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Locale;

public final class CompetitionSpecifications {

    private CompetitionSpecifications() {
    }

    public static Specification<Competition> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            String pattern = containsPattern(keyword);
            if (pattern == null) {
                return null;
            }
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, '\\'),
                    cb.like(cb.lower(root.get("organizer")), pattern, '\\'),
                    cb.like(cb.lower(root.get("kind")), pattern, '\\')
            );
        };
    }

    public static Specification<Competition> hasSourceProvider(String sourceProvider) {
        return containsIgnoreCase("sourceProvider", sourceProvider);
    }

    public static Specification<Competition> hasCompetitionFormat(CompetitionFormat competitionFormat) {
        return (root, query, cb) -> competitionFormat == null
                ? null
                : cb.equal(root.get("competitionFormat"), competitionFormat);
    }

    public static Specification<Competition> hasPhase(CompetitionPhase phase) {
        return (root, query, cb) -> phase == null ? null : cb.equal(root.get("phase"), phase);
    }

    public static Specification<Competition> hasCountry(String country) {
        return containsIgnoreCase("country", country);
    }

    public static Specification<Competition> hasCity(String city) {
        return containsIgnoreCase("city", city);
    }

    public static Specification<Competition> startsAtOrAfter(Instant startsAfter) {
        return (root, query, cb) -> startsAfter == null
                ? null
                : cb.greaterThanOrEqualTo(root.get("startsAt"), startsAfter);
    }

    public static Specification<Competition> startsAtOrBefore(Instant startsBefore) {
        return (root, query, cb) -> startsBefore == null
                ? null
                : cb.lessThanOrEqualTo(root.get("startsAt"), startsBefore);
    }

    public static Specification<Competition> hasActiveStatus(Boolean active) {
        return (root, query, cb) -> active == null ? null : cb.equal(root.get("active"), active);
    }

    private static Specification<Competition> containsIgnoreCase(String field, String value) {
        return (root, query, cb) -> {
            String pattern = containsPattern(value);
            return pattern == null ? null : cb.like(cb.lower(root.get(field)), pattern, '\\');
        };
    }

    private static String containsPattern(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String escaped = value.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}

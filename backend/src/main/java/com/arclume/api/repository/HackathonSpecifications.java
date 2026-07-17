package com.arclume.api.repository;

import com.arclume.api.domain.Hackathon;
import com.arclume.api.domain.HackathonMode;
import com.arclume.api.domain.HackathonOrganizerType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

public final class HackathonSpecifications {

    private HackathonSpecifications() {
    }

    public static Specification<Hackathon> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            String pattern = containsPattern(keyword);
            if (pattern == null) {
                return null;
            }
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, '\\'),
                    cb.like(cb.lower(root.get("description")), pattern, '\\')
            );
        };
    }

    public static Specification<Hackathon> hasOrganizer(String organizer) {
        return containsIgnoreCase("organizer", organizer);
    }

    public static Specification<Hackathon> hasCity(String city) {
        return containsIgnoreCase("city", city);
    }

    public static Specification<Hackathon> hasRegion(String region) {
        return containsIgnoreCase("region", region);
    }

    public static Specification<Hackathon> hasCountry(String country) {
        return containsIgnoreCase("country", country);
    }

    public static Specification<Hackathon> hasSourceProvider(String sourceProvider) {
        return containsIgnoreCase("sourceProvider", sourceProvider);
    }

    public static Specification<Hackathon> hasMode(HackathonMode mode) {
        return (root, query, cb) -> mode == null ? null : cb.equal(root.get("mode"), mode);
    }

    public static Specification<Hackathon> hasOrganizerType(HackathonOrganizerType organizerType) {
        return (root, query, cb) -> organizerType == null
                ? null
                : cb.equal(root.get("organizerType"), organizerType);
    }

    public static Specification<Hackathon> hasMinimumPrize(BigDecimal minimum) {
        return (root, query, cb) -> minimum == null
                ? null
                : cb.greaterThanOrEqualTo(root.get("prizePoolAmount"), minimum);
    }

    public static Specification<Hackathon> hasMaximumPrize(BigDecimal maximum) {
        return (root, query, cb) -> maximum == null
                ? null
                : cb.lessThanOrEqualTo(root.get("prizePoolAmount"), maximum);
    }

    public static Specification<Hackathon> startsOnOrAfter(LocalDate startsAfter) {
        return (root, query, cb) -> startsAfter == null
                ? null
                : cb.greaterThanOrEqualTo(root.get("startDate"), startsAfter);
    }

    public static Specification<Hackathon> startsOnOrBefore(LocalDate startsBefore) {
        return (root, query, cb) -> startsBefore == null
                ? null
                : cb.lessThanOrEqualTo(root.get("startDate"), startsBefore);
    }

    public static Specification<Hackathon> hasActiveStatus(Boolean active) {
        return (root, query, cb) -> active == null ? null : cb.equal(root.get("active"), active);
    }

    private static Specification<Hackathon> containsIgnoreCase(String field, String value) {
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


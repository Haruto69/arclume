package com.arclume.api.repository;

import com.arclume.api.domain.BenefitType;
import com.arclume.api.domain.StudentProgram;
import com.arclume.api.domain.StudentProgramMode;
import com.arclume.api.domain.StudentProgramType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Locale;

public final class StudentProgramSpecifications {

    private StudentProgramSpecifications() {
    }

    public static Specification<StudentProgram> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            String pattern = containsPattern(keyword);
            if (pattern == null) {
                return null;
            }
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, '\\'),
                    cb.like(cb.lower(root.get("company")), pattern, '\\'),
                    cb.like(cb.lower(root.get("description")), pattern, '\\'),
                    cb.like(cb.lower(root.get("benefitSummary")), pattern, '\\')
            );
        };
    }

    public static Specification<StudentProgram> hasCompany(String company) {
        return containsIgnoreCase("company", company);
    }

    public static Specification<StudentProgram> hasRegion(String region) {
        return containsIgnoreCase("region", region);
    }

    public static Specification<StudentProgram> hasCountry(String country) {
        return containsIgnoreCase("country", country);
    }

    public static Specification<StudentProgram> hasProgramType(StudentProgramType programType) {
        return (root, query, cb) -> programType == null ? null : cb.equal(root.get("programType"), programType);
    }

    public static Specification<StudentProgram> hasMode(StudentProgramMode mode) {
        return (root, query, cb) -> mode == null ? null : cb.equal(root.get("mode"), mode);
    }

    public static Specification<StudentProgram> hasBenefitType(BenefitType benefitType) {
        return (root, query, cb) -> benefitType == null
                ? null
                : cb.isTrue(cb.function(
                        "jsonb_exists",
                        Boolean.class,
                        root.get("benefitTypes"),
                        cb.literal(benefitType.name())
                ));
    }

    public static Specification<StudentProgram> hasAlwaysOpenStatus(Boolean alwaysOpen) {
        return (root, query, cb) -> alwaysOpen == null ? null : cb.equal(root.get("alwaysOpen"), alwaysOpen);
    }

    public static Specification<StudentProgram> hasActiveStatus(Boolean active) {
        return (root, query, cb) -> active == null ? null : cb.equal(root.get("active"), active);
    }

    public static Specification<StudentProgram> deadlineOnOrBefore(LocalDate deadline) {
        return (root, query, cb) -> deadline == null
                ? null
                : cb.lessThanOrEqualTo(root.get("applicationDeadline"), deadline);
    }

    public static Specification<StudentProgram> deadlineOnOrAfter(LocalDate deadline) {
        return (root, query, cb) -> deadline == null
                ? null
                : cb.greaterThanOrEqualTo(root.get("applicationDeadline"), deadline);
    }

    private static Specification<StudentProgram> containsIgnoreCase(String field, String value) {
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

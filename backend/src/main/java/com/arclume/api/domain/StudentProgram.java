package com.arclume.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "student_programs")
public class StudentProgram extends BaseEntity {

    @NotBlank
    @Size(max = 250)
    @Column(nullable = false, length = 250)
    private String title;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String company;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "program_type", nullable = false, length = 50)
    private StudentProgramType programType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StudentProgramMode mode;

    @Size(max = 150)
    @Column(length = 150)
    private String region;

    @Size(max = 150)
    @Column(length = 150)
    private String country;

    @Column(columnDefinition = "TEXT")
    private String eligibility;

    @Column(name = "benefit_summary", columnDefinition = "TEXT")
    private String benefitSummary;

    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @NotNull
    @Column(name = "always_open", nullable = false)
    private Boolean alwaysOpen = false;

    @NotBlank
    @Size(max = 1000)
    @Column(name = "external_url", nullable = false, length = 1000)
    private String externalUrl;

    @NotBlank
    @Size(max = 100)
    @Column(name = "source_provider", nullable = false, length = 100)
    private String sourceProvider;

    @Size(max = 200)
    @Column(name = "source_id", length = 200)
    private String sourceId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Size(max = 50)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benefit_types", nullable = false, columnDefinition = "jsonb")
    private List<@Size(max = 100) String> benefitTypes = new ArrayList<>();

    @Size(max = 50)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<@Size(max = 100) String> tags = new ArrayList<>();

    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @Column(name = "last_verified_at")
    private LocalDate lastVerifiedAt;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public StudentProgramType getProgramType() {
        return programType;
    }

    public void setProgramType(StudentProgramType programType) {
        this.programType = programType;
    }

    public StudentProgramMode getMode() {
        return mode;
    }

    public void setMode(StudentProgramMode mode) {
        this.mode = mode;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getEligibility() {
        return eligibility;
    }

    public void setEligibility(String eligibility) {
        this.eligibility = eligibility;
    }

    public String getBenefitSummary() {
        return benefitSummary;
    }

    public void setBenefitSummary(String benefitSummary) {
        this.benefitSummary = benefitSummary;
    }

    public LocalDate getApplicationDeadline() {
        return applicationDeadline;
    }

    public void setApplicationDeadline(LocalDate applicationDeadline) {
        this.applicationDeadline = applicationDeadline;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Boolean getAlwaysOpen() {
        return alwaysOpen;
    }

    public void setAlwaysOpen(Boolean alwaysOpen) {
        this.alwaysOpen = alwaysOpen;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    public void setSourceProvider(String sourceProvider) {
        this.sourceProvider = normalizeUppercase(sourceProvider);
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = normalizeNullable(sourceId);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getBenefitTypes() {
        return benefitTypes;
    }

    public void setBenefitTypes(List<String> benefitTypes) {
        this.benefitTypes = normalizeList(benefitTypes, true);
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = normalizeList(tags, false);
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDate getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(LocalDate lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    private List<String> normalizeList(List<String> values, boolean uppercase) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(value -> value != null && !value.trim().isEmpty())
                .map(String::trim)
                .map(value -> uppercase ? value.toUpperCase(Locale.ROOT) : value)
                .distinct()
                .toList();
    }

    private String normalizeUppercase(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}

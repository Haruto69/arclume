package com.arclume.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Entity
@Table(
        name = "hackathons",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_hackathon_source",
                columnNames = {"source_provider", "source_id"}
        )
)
public class Hackathon extends BaseEntity {

    @NotBlank
    @Size(max = 250)
    @Column(nullable = false, length = 250)
    private String title;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String organizer;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "organizer_type", nullable = false, length = 50)
    private HackathonOrganizerType organizerType;

    @Size(max = 150)
    @Column(length = 150)
    private String city;

    @Size(max = 150)
    @Column(length = 150)
    private String region;

    @Size(max = 150)
    @Column(length = 150)
    private String country;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private HackathonMode mode;

    @DecimalMin("0.0")
    @Column(name = "prize_pool_amount", precision = 19, scale = 2)
    private BigDecimal prizePoolAmount;

    @Size(min = 3, max = 3)
    @Column(name = "prize_pool_currency", length = 3)
    private String prizePoolCurrency;

    @Column(name = "registration_deadline")
    private LocalDate registrationDeadline;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Size(max = 1000)
    @Column(name = "external_url", length = 1000)
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
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<@Size(max = 100) String> tags = new ArrayList<>();

    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOrganizer() {
        return organizer;
    }

    public void setOrganizer(String organizer) {
        this.organizer = organizer;
    }

    public HackathonOrganizerType getOrganizerType() {
        return organizerType;
    }

    public void setOrganizerType(HackathonOrganizerType organizerType) {
        this.organizerType = organizerType;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
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

    public HackathonMode getMode() {
        return mode;
    }

    public void setMode(HackathonMode mode) {
        this.mode = mode;
    }

    public BigDecimal getPrizePoolAmount() {
        return prizePoolAmount;
    }

    public void setPrizePoolAmount(BigDecimal prizePoolAmount) {
        this.prizePoolAmount = prizePoolAmount;
    }

    public String getPrizePoolCurrency() {
        return prizePoolCurrency;
    }

    public void setPrizePoolCurrency(String prizePoolCurrency) {
        this.prizePoolCurrency = normalizeUppercase(prizePoolCurrency);
    }

    public LocalDate getRegistrationDeadline() {
        return registrationDeadline;
    }

    public void setRegistrationDeadline(LocalDate registrationDeadline) {
        this.registrationDeadline = registrationDeadline;
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

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        if (tags == null) {
            this.tags = new ArrayList<>();
            return;
        }
        this.tags = tags.stream()
                .filter(tag -> tag != null && !tag.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .toList();
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    private String normalizeUppercase(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}


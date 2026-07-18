package com.arclume.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(
        name = "competitions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_competitions_source",
                columnNames = {"source_provider", "source_id"}
        )
)
public class Competition extends BaseEntity {

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
    @Column(name = "competition_format", nullable = false, length = 30)
    private CompetitionFormat competitionFormat;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CompetitionPhase phase;

    @Size(max = 200)
    @Column(length = 200)
    private String kind;

    @Min(1)
    @Max(5)
    @Column
    private Integer difficulty;

    @Size(max = 150)
    @Column(length = 150)
    private String city;

    @Size(max = 150)
    @Column(length = 150)
    private String country;

    @NotNull
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @NotNull
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Positive
    @Column(name = "duration_seconds", nullable = false)
    private long durationSeconds;

    @NotBlank
    @Size(max = 1000)
    @Column(name = "external_url", nullable = false, length = 1000)
    private String externalUrl;

    @NotBlank
    @Size(max = 100)
    @Column(name = "source_provider", nullable = false, length = 100)
    private String sourceProvider;

    @NotBlank
    @Size(max = 200)
    @Column(name = "source_id", nullable = false, length = 200)
    private String sourceId;

    @Size(max = 120)
    @Column(name = "attribution_label", length = 120)
    private String attributionLabel;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = normalizeNullable(title);
    }

    public String getOrganizer() {
        return organizer;
    }

    public void setOrganizer(String organizer) {
        this.organizer = normalizeNullable(organizer);
    }

    public CompetitionFormat getCompetitionFormat() {
        return competitionFormat;
    }

    public void setCompetitionFormat(CompetitionFormat competitionFormat) {
        this.competitionFormat = competitionFormat;
    }

    public CompetitionPhase getPhase() {
        return phase;
    }

    public void setPhase(CompetitionPhase phase) {
        this.phase = phase;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = normalizeNullable(kind);
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = normalizeNullable(city);
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = normalizeNullable(country);
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = normalizeNullable(externalUrl);
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

    public String getAttributionLabel() {
        return attributionLabel;
    }

    public void setAttributionLabel(String attributionLabel) {
        this.attributionLabel = normalizeNullable(attributionLabel);
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(Instant syncedAt) {
        this.syncedAt = syncedAt;
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

package com.arclume.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Entity
@Table(name = "opportunity_sync_runs")
public class OpportunitySyncRun extends BaseEntity {

    public static final int MAX_ERROR_LENGTH = 1000;

    private static final Pattern URL_QUERY_PATTERN = Pattern.compile(
            "(?i)\\b(https?://[^\\s?]+\\?)[^\\s]+"
    );
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile(
            "(?i)\\b(authorization\\s*[:=]\\s*)(?:bearer\\s+)?[^\\s,;]+"
    );
    private static final Pattern COOKIE_PATTERN = Pattern.compile(
            "(?i)\\b((?:set-cookie|cookie)\\s*[:=]\\s*)[^\\s,]+(?:;\\s*[^\\s,]+)*"
    );
    private static final Pattern SENSITIVE_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)\\b((?:api[_-]?key|access[_-]?token|token|secret|password)\\s*[:=]\\s*)[^\\s,;&]+"
    );
    private static final Pattern BEARER_PATTERN = Pattern.compile(
            "(?i)\\bBearer\\s+[^\\s,;]+"
    );
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b"
    );

    @NotBlank
    @Size(max = 100)
    @Column(name = "provider_key", nullable = false, length = 100)
    private String providerKey;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OpportunityCategory category;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OpportunitySyncStatus status = OpportunitySyncStatus.RUNNING;

    @NotNull
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Min(0)
    @Column(name = "records_fetched", nullable = false)
    private int recordsFetched;

    @Min(0)
    @Column(name = "records_created", nullable = false)
    private int recordsCreated;

    @Min(0)
    @Column(name = "records_updated", nullable = false)
    private int recordsUpdated;

    @Min(0)
    @Column(name = "records_skipped", nullable = false)
    private int recordsSkipped;

    @Min(0)
    @Column(name = "records_failed", nullable = false)
    private int recordsFailed;

    @Min(0)
    @Column(name = "records_deactivated", nullable = false)
    private int recordsDeactivated;

    @Size(max = MAX_ERROR_LENGTH)
    @Column(name = "sync_error", length = MAX_ERROR_LENGTH)
    private String syncError;

    public static OpportunitySyncRun start(String providerKey, OpportunityCategory category, Instant startedAt) {
        OpportunitySyncRun run = new OpportunitySyncRun();
        run.setProviderKey(providerKey);
        run.setCategory(category);
        run.setStatus(OpportunitySyncStatus.RUNNING);
        run.setStartedAt(startedAt);
        return run;
    }

    public void complete(OpportunitySyncStatus finalStatus, OpportunitySyncCounters counters, Instant finishedAt, String error) {
        if (finalStatus == OpportunitySyncStatus.RUNNING) {
            throw new IllegalArgumentException("Completed sync runs cannot use RUNNING status");
        }
        setStatus(finalStatus);
        setCounters(counters);
        setFinishedAt(finishedAt);
        setSyncError(error);
    }

    public void setCounters(OpportunitySyncCounters counters) {
        if (counters == null) {
            return;
        }
        setRecordsFetched(counters.recordsFetched());
        setRecordsCreated(counters.recordsCreated());
        setRecordsUpdated(counters.recordsUpdated());
        setRecordsSkipped(counters.recordsSkipped());
        setRecordsFailed(counters.recordsFailed());
        setRecordsDeactivated(counters.recordsDeactivated());
    }

    public static String sanitizeError(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String sanitized = value.replaceAll("[\\r\\n\\t]+", " ");
        sanitized = redactWithPrefix(URL_QUERY_PATTERN, sanitized);
        sanitized = redactWithPrefix(AUTHORIZATION_PATTERN, sanitized);
        sanitized = redactWithPrefix(COOKIE_PATTERN, sanitized);
        sanitized = redactWithPrefix(SENSITIVE_ASSIGNMENT_PATTERN, sanitized);
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("Bearer [REDACTED]");
        sanitized = JWT_PATTERN.matcher(sanitized).replaceAll("[REDACTED_JWT]");
        sanitized = sanitized.trim();

        if (sanitized.isBlank()) {
            return null;
        }
        if (sanitized.length() <= MAX_ERROR_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_ERROR_LENGTH);
    }

    private static String redactWithPrefix(Pattern pattern, String value) {
        return pattern.matcher(value)
                .replaceAll(match -> Matcher.quoteReplacement(match.group(1) + "[REDACTED]"));
    }

    public String getProviderKey() {
        return providerKey;
    }

    public void setProviderKey(String providerKey) {
        if (providerKey == null || providerKey.trim().isEmpty()) {
            this.providerKey = null;
            return;
        }
        this.providerKey = providerKey.trim().toUpperCase(Locale.ROOT);
    }

    public OpportunityCategory getCategory() {
        return category;
    }

    public void setCategory(OpportunityCategory category) {
        this.category = category;
    }

    public OpportunitySyncStatus getStatus() {
        return status;
    }

    public void setStatus(OpportunitySyncStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getRecordsFetched() {
        return recordsFetched;
    }

    public void setRecordsFetched(int recordsFetched) {
        this.recordsFetched = requireNonNegative(recordsFetched, "recordsFetched");
    }

    public int getRecordsCreated() {
        return recordsCreated;
    }

    public void setRecordsCreated(int recordsCreated) {
        this.recordsCreated = requireNonNegative(recordsCreated, "recordsCreated");
    }

    public int getRecordsUpdated() {
        return recordsUpdated;
    }

    public void setRecordsUpdated(int recordsUpdated) {
        this.recordsUpdated = requireNonNegative(recordsUpdated, "recordsUpdated");
    }

    public int getRecordsSkipped() {
        return recordsSkipped;
    }

    public void setRecordsSkipped(int recordsSkipped) {
        this.recordsSkipped = requireNonNegative(recordsSkipped, "recordsSkipped");
    }

    public int getRecordsFailed() {
        return recordsFailed;
    }

    public void setRecordsFailed(int recordsFailed) {
        this.recordsFailed = requireNonNegative(recordsFailed, "recordsFailed");
    }

    public int getRecordsDeactivated() {
        return recordsDeactivated;
    }

    public void setRecordsDeactivated(int recordsDeactivated) {
        this.recordsDeactivated = requireNonNegative(recordsDeactivated, "recordsDeactivated");
    }

    public String getSyncError() {
        return syncError;
    }

    public void setSyncError(String syncError) {
        this.syncError = sanitizeError(syncError);
    }

    private int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
        return value;
    }
}

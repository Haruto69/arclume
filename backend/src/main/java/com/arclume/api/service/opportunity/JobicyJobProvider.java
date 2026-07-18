package com.arclume.api.service.opportunity;

import com.arclume.api.client.JobicyJobClient;
import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.OpportunityCategory;
import com.arclume.api.domain.WorkMode;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class JobicyJobProvider implements OpportunityProvider {

    public static final String PROVIDER_KEY = "JOBICY";
    public static final String ATTRIBUTION_LABEL = "Jobs provided by Jobicy";
    private static final DateTimeFormatter UTC_SPACE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JobicyJobClient jobicyJobClient;
    private final OpportunityProviderProperties properties;

    public JobicyJobProvider(JobicyJobClient jobicyJobClient, OpportunityProviderProperties properties) {
        this.jobicyJobClient = jobicyJobClient;
        this.properties = properties;
    }

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }

    @Override
    public OpportunityCategory category() {
        return OpportunityCategory.JOB;
    }

    @Override
    public boolean isEnabled() {
        return properties.getProviders().getJobicy().isEnabled();
    }

    @Override
    public OpportunityProviderFetchResult fetchOpportunities() {
        List<JobicyJobClient.JobicyJob> jobicyJobs = jobicyJobClient.fetchJobs();
        List<NormalizedOpportunityRecord> records = new ArrayList<>();
        int skipped = 0;

        for (JobicyJobClient.JobicyJob jobicyJob : jobicyJobs) {
            NormalizedJobOpportunity normalized = normalize(jobicyJob);
            if (normalized == null) {
                skipped++;
                continue;
            }
            records.add(normalized);
        }

        return new OpportunityProviderFetchResult(jobicyJobs.size(), skipped, records);
    }

    private NormalizedJobOpportunity normalize(JobicyJobClient.JobicyJob jobicyJob) {
        String sourceId = normalizeRequired(jobicyJob.getId());
        String sourceUrl = normalizeRequired(jobicyJob.getUrl());
        String title = normalizeRequired(jobicyJob.getJobTitle());
        String company = normalizeRequired(jobicyJob.getCompanyName());

        if (sourceId == null || sourceUrl == null || title == null || company == null) {
            return null;
        }

        return new NormalizedJobOpportunity(
                sourceId,
                sourceUrl,
                ATTRIBUTION_LABEL,
                title,
                company,
                normalizeOptional(jobicyJob.getJobGeo()),
                mapEmploymentType(jobicyJob.getJobType()),
                WorkMode.REMOTE,
                formatSalary(jobicyJob),
                parseInstant(jobicyJob.getPubDate()),
                sanitizeHtml(jobicyJob.getJobDescription()),
                "",
                true
        );
    }

    private EmploymentType mapEmploymentType(String jobType) {
        if (jobType == null) {
            return null;
        }
        String normalized = jobType.toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        return switch (normalized) {
            case "full_time", "fulltime" -> EmploymentType.FULL_TIME;
            case "part_time", "parttime" -> EmploymentType.PART_TIME;
            case "contract" -> EmploymentType.CONTRACT;
            case "internship", "intern" -> EmploymentType.INTERNSHIP;
            default -> null;
        };
    }

    private String formatSalary(JobicyJobClient.JobicyJob jobicyJob) {
        BigDecimal min = jobicyJob.getSalaryMin();
        BigDecimal max = jobicyJob.getSalaryMax();
        if (min == null && max == null) {
            return null;
        }

        String currency = normalizeOptional(jobicyJob.getSalaryCurrency());
        String period = normalizeOptional(jobicyJob.getSalaryPeriod());
        String formatted;

        if (min != null && max != null) {
            formatted = amountWithCurrency(currency, min) + " - " + formatNumber(max);
        } else if (min != null) {
            formatted = amountWithCurrency(currency, min);
        } else {
            formatted = "Up to " + amountWithCurrency(currency, max);
        }

        if (period != null) {
            formatted += " / " + period;
        }
        return formatted;
    }

    private String amountWithCurrency(String currency, BigDecimal value) {
        String amount = formatNumber(value);
        return currency == null ? amount : currency + " " + amount;
    }

    private String formatNumber(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String sanitizeHtml(String html) {
        if (html == null) {
            return null;
        }
        return Jsoup.clean(html, Safelist.basic());
    }

    private Instant parseInstant(String dateValue) {
        String normalized = normalizeOptional(dateValue);
        if (normalized == null) {
            return null;
        }

        try {
            return Instant.parse(normalized);
        } catch (Exception ignored) {
            // Try other documented ISO/UTC forms below without using the machine local timezone.
        }
        try {
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (Exception ignored) {
            // Continue to RFC 1123 and UTC-local variants.
        }
        try {
            return ZonedDateTime.parse(normalized, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception ignored) {
            // Continue to UTC-local timestamp variants.
        }
        try {
            return LocalDateTime.parse(normalized, UTC_SPACE_TIMESTAMP).toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            // Continue to standard local ISO timestamp parsing as UTC.
        }
        try {
            return LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeRequired(String value) {
        return normalizeOptional(value);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}

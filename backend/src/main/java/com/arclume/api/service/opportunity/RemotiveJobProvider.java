package com.arclume.api.service.opportunity;

import com.arclume.api.client.RemotiveJobClient;
import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.OpportunityCategory;
import com.arclume.api.domain.WorkMode;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
public class RemotiveJobProvider implements OpportunityProvider {

    public static final String PROVIDER_KEY = "REMOTIVE";
    public static final String ATTRIBUTION_LABEL = "Jobs provided by Remotive";

    private final RemotiveJobClient remotiveJobClient;
    private final OpportunityProviderProperties properties;

    public RemotiveJobProvider(RemotiveJobClient remotiveJobClient, OpportunityProviderProperties properties) {
        this.remotiveJobClient = remotiveJobClient;
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
        return properties.getProviders().getRemotive().isEnabled();
    }

    @Override
    public OpportunityProviderFetchResult fetchOpportunities() {
        List<RemotiveJobClient.RemotiveJob> remotiveJobs = remotiveJobClient.fetchJobs();
        List<NormalizedOpportunityRecord> records = new ArrayList<>();
        int skipped = 0;

        for (RemotiveJobClient.RemotiveJob remotiveJob : remotiveJobs) {
            NormalizedJobOpportunity normalized = normalize(remotiveJob);
            if (normalized == null) {
                skipped++;
                continue;
            }
            records.add(normalized);
        }

        return new OpportunityProviderFetchResult(remotiveJobs.size(), skipped, records);
    }

    private NormalizedJobOpportunity normalize(RemotiveJobClient.RemotiveJob remotiveJob) {
        String sourceId = normalizeRequired(remotiveJob.getId());
        String title = normalizeRequired(remotiveJob.getTitle());
        String company = normalizeRequired(remotiveJob.getCompanyName());

        if (sourceId == null || title == null || company == null) {
            return null;
        }

        return new NormalizedJobOpportunity(
                sourceId,
                normalizeOptional(remotiveJob.getUrl()),
                ATTRIBUTION_LABEL,
                title,
                company,
                normalizeOptional(remotiveJob.getCandidateRequiredLocation()),
                mapEmploymentType(remotiveJob.getJobType()),
                WorkMode.REMOTE,
                normalizeOptional(remotiveJob.getSalary()),
                parseInstant(remotiveJob.getPublicationDate()),
                sanitizeHtml(remotiveJob.getDescription()),
                "",
                true
        );
    }

    private EmploymentType mapEmploymentType(String jobType) {
        if (jobType == null) {
            return null;
        }
        String normalized = jobType.toLowerCase().replace("-", "_").replace(" ", "_");
        return switch (normalized) {
            case "full_time", "fulltime" -> EmploymentType.FULL_TIME;
            case "part_time", "parttime" -> EmploymentType.PART_TIME;
            case "contract" -> EmploymentType.CONTRACT;
            case "internship" -> EmploymentType.INTERNSHIP;
            default -> null;
        };
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
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC);
            } catch (Exception ignored) {
                return null;
            }
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

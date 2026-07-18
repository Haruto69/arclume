package com.arclume.api.service.opportunity;

import com.arclume.api.client.GreenhouseJobClient;
import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.domain.OpportunityCategory;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class GreenhouseJobProvider implements OpportunityProvider {

    public static final String PROVIDER_KEY = "GREENHOUSE";
    public static final String ATTRIBUTION_LABEL = "Job listing via Greenhouse";

    private static final Pattern SOURCE_IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final int MAX_EXTERNAL_ID_LENGTH = 100;
    private static final int MAX_COMPANY_NAME_LENGTH = 200;
    private static final int MAX_ENTITY_DECODE_PASSES = 2;

    private final GreenhouseJobClient greenhouseJobClient;
    private final OpportunityProviderProperties properties;

    public GreenhouseJobProvider(GreenhouseJobClient greenhouseJobClient, OpportunityProviderProperties properties) {
        this.greenhouseJobClient = greenhouseJobClient;
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
        return properties.getProviders().getGreenhouse().isEnabled();
    }

    @Override
    public OpportunityProviderFetchResult fetchOpportunities() {
        SourceValidation validation = validateEnabledSources();
        List<NormalizedOpportunityRecord> records = new ArrayList<>();
        Set<String> seenSourceIds = new HashSet<>();
        int recordsFetched = 0;
        int recordsSkipped = 0;
        int recordsFailed = validation.recordsFailed();
        int sourcesSucceeded = 0;

        for (ValidatedSource source : validation.sources()) {
            List<GreenhouseJobClient.GreenhouseJob> jobs;
            try {
                jobs = greenhouseJobClient.fetchJobs(source.boardToken());
                sourcesSucceeded++;
            } catch (Exception e) {
                recordsFailed++;
                continue;
            }

            recordsFetched += jobs.size();
            for (GreenhouseJobClient.GreenhouseJob job : jobs) {
                NormalizedJobOpportunity normalized = normalize(job, source);
                if (normalized == null || !seenSourceIds.add(normalized.sourceId())) {
                    recordsSkipped++;
                    continue;
                }
                records.add(normalized);
            }
        }

        if (sourcesSucceeded == 0) {
            throw new OpportunityProviderException("Greenhouse provider failed to fetch configured sources", null);
        }

        return new OpportunityProviderFetchResult(recordsFetched, recordsSkipped, recordsFailed, records);
    }

    private SourceValidation validateEnabledSources() {
        List<OpportunityProviderProperties.GreenhouseSource> configuredSources =
                properties.getProviders().getGreenhouse().getSources();
        List<ValidatedSource> validSources = new ArrayList<>();
        int recordsFailed = 0;
        boolean hasEnabledSource = false;

        for (OpportunityProviderProperties.GreenhouseSource source : configuredSources) {
            if (!source.isEnabled()) {
                continue;
            }
            hasEnabledSource = true;

            String boardToken = normalizeOptional(source.getBoardToken());
            String companyName = normalizeOptional(source.getCompanyName());
            if (!isValidSourceIdentifier(boardToken) || !isValidCompanyName(companyName)) {
                recordsFailed++;
                continue;
            }

            validSources.add(new ValidatedSource(
                    boardToken,
                    boardToken.toLowerCase(Locale.ROOT),
                    companyName));
        }

        if (!hasEnabledSource) {
            throw new OpportunityProviderException("Greenhouse provider requires at least one enabled source", null);
        }
        if (validSources.isEmpty()) {
            throw new OpportunityProviderException("Greenhouse provider requires at least one valid enabled source", null);
        }
        return new SourceValidation(validSources, recordsFailed);
    }

    private NormalizedJobOpportunity normalize(
            GreenhouseJobClient.GreenhouseJob job,
            ValidatedSource source) {
        String postId = normalizeRequired(job.getId());
        String sourceUrl = normalizeRequired(job.getAbsoluteUrl());
        String title = normalizeRequired(job.getTitle());
        String sourceId = compositeSourceId(source.identityBoardToken(), postId);

        if (sourceId == null || sourceUrl == null || title == null) {
            return null;
        }

        return new NormalizedJobOpportunity(
                sourceId,
                sourceUrl,
                ATTRIBUTION_LABEL,
                title,
                source.companyName(),
                normalizeLocation(job),
                null,
                null,
                null,
                null,
                sanitizeHtml(job.getContent()),
                "",
                true
        );
    }

    private String compositeSourceId(String boardToken, String postId) {
        if (postId == null) {
            return null;
        }
        String composite = boardToken + ":" + postId;
        return composite.length() <= MAX_EXTERNAL_ID_LENGTH ? composite : null;
    }

    private String normalizeLocation(GreenhouseJobClient.GreenhouseJob job) {
        if (job.getLocation() == null) {
            return null;
        }
        return normalizeOptional(job.getLocation().getName());
    }

    private String sanitizeHtml(String html) {
        if (html == null) {
            return null;
        }
        return Jsoup.clean(decodeHtmlEntities(html), Safelist.basic());
    }

    private String decodeHtmlEntities(String value) {
        String current = value;
        for (int i = 0; i < MAX_ENTITY_DECODE_PASSES; i++) {
            String decoded = Parser.unescapeEntities(current, false);
            if (decoded.equals(current)) {
                return current;
            }
            current = decoded;
        }
        return current;
    }

    private boolean isValidSourceIdentifier(String value) {
        return value != null && SOURCE_IDENTIFIER_PATTERN.matcher(value).matches();
    }

    private boolean isValidCompanyName(String value) {
        return value != null && value.length() <= MAX_COMPANY_NAME_LENGTH;
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

    private record ValidatedSource(String boardToken, String identityBoardToken, String companyName) {
    }

    private record SourceValidation(List<ValidatedSource> sources, int recordsFailed) {
    }
}

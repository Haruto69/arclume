package com.arclume.api.service.opportunity;

import com.arclume.api.client.LeverJobClient;
import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.config.OpportunityProviderProperties.LeverRegion;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.OpportunityCategory;
import com.arclume.api.domain.WorkMode;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class LeverJobProvider implements OpportunityProvider {

    public static final String PROVIDER_KEY = "LEVER";
    public static final String ATTRIBUTION_LABEL = "Job listing via Lever";

    private static final Pattern SOURCE_IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final int MAX_EXTERNAL_ID_LENGTH = 100;
    private static final int MAX_COMPANY_NAME_LENGTH = 200;
    private static final int MAX_SALARY_DESCRIPTION_LENGTH = 255;

    private final LeverJobClient leverJobClient;
    private final OpportunityProviderProperties properties;

    public LeverJobProvider(LeverJobClient leverJobClient, OpportunityProviderProperties properties) {
        this.leverJobClient = leverJobClient;
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
        return properties.getProviders().getLever().isEnabled();
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
            List<LeverJobClient.LeverPosting> postings;
            try {
                postings = leverJobClient.fetchJobs(source.site(), source.region());
                sourcesSucceeded++;
            } catch (Exception e) {
                recordsFailed++;
                continue;
            }

            recordsFetched += postings.size();
            for (LeverJobClient.LeverPosting posting : postings) {
                NormalizedJobOpportunity normalized = normalize(posting, source);
                if (normalized == null || !seenSourceIds.add(normalized.sourceId())) {
                    recordsSkipped++;
                    continue;
                }
                records.add(normalized);
            }
        }

        if (sourcesSucceeded == 0) {
            throw new OpportunityProviderException("Lever provider failed to fetch configured sources", null);
        }

        return new OpportunityProviderFetchResult(recordsFetched, recordsSkipped, recordsFailed, records);
    }

    private SourceValidation validateEnabledSources() {
        List<OpportunityProviderProperties.LeverSource> configuredSources =
                properties.getProviders().getLever().getSources();
        List<ValidatedSource> validSources = new ArrayList<>();
        int recordsFailed = 0;
        boolean hasEnabledSource = false;

        for (OpportunityProviderProperties.LeverSource source : configuredSources) {
            if (!source.isEnabled()) {
                continue;
            }
            hasEnabledSource = true;

            String site = normalizeOptional(source.getSite());
            String companyName = normalizeOptional(source.getCompanyName());
            LeverRegion region = source.getRegion() == null ? LeverRegion.GLOBAL : source.getRegion();
            if (!isValidSourceIdentifier(site) || !isValidCompanyName(companyName)) {
                recordsFailed++;
                continue;
            }

            validSources.add(new ValidatedSource(
                    site,
                    site.toLowerCase(Locale.ROOT),
                    companyName,
                    region,
                    region.name().toLowerCase(Locale.ROOT)));
        }

        if (!hasEnabledSource) {
            throw new OpportunityProviderException("Lever provider requires at least one enabled source", null);
        }
        if (validSources.isEmpty()) {
            throw new OpportunityProviderException("Lever provider requires at least one valid enabled source", null);
        }
        return new SourceValidation(validSources, recordsFailed);
    }

    private NormalizedJobOpportunity normalize(
            LeverJobClient.LeverPosting posting,
            ValidatedSource source) {
        String postingId = normalizeRequired(posting.getId());
        String sourceUrl = normalizeRequired(posting.getHostedUrl());
        String title = normalizeRequired(posting.getText());
        String sourceId = compositeSourceId(source.identityRegion(), source.identitySite(), postingId);

        if (sourceId == null || sourceUrl == null || title == null) {
            return null;
        }

        return new NormalizedJobOpportunity(
                sourceId,
                sourceUrl,
                ATTRIBUTION_LABEL,
                title,
                source.companyName(),
                normalizeLocation(posting),
                mapEmploymentType(posting),
                mapWorkMode(posting.getWorkplaceType()),
                formatSalary(posting),
                null,
                assembleDescription(posting),
                "",
                true
        );
    }

    private String compositeSourceId(String region, String site, String postingId) {
        if (postingId == null) {
            return null;
        }
        String composite = region + ":" + site + ":" + postingId;
        return composite.length() <= MAX_EXTERNAL_ID_LENGTH ? composite : null;
    }

    private String normalizeLocation(LeverJobClient.LeverPosting posting) {
        LeverJobClient.LeverCategories categories = posting.getCategories();
        if (categories == null) {
            return normalizeOptional(posting.getCountry());
        }

        if (categories.getAllLocations() != null) {
            LinkedHashSet<String> allLocations = new LinkedHashSet<>();
            for (String location : categories.getAllLocations()) {
                String normalized = normalizeOptional(location);
                if (normalized != null) {
                    allLocations.add(normalized);
                }
            }
            if (!allLocations.isEmpty()) {
                return String.join(", ", allLocations);
            }
        }

        String categoryLocation = normalizeOptional(categories.getLocation());
        if (categoryLocation != null) {
            return categoryLocation;
        }
        return normalizeOptional(posting.getCountry());
    }

    private EmploymentType mapEmploymentType(LeverJobClient.LeverPosting posting) {
        LeverJobClient.LeverCategories categories = posting.getCategories();
        if (categories == null) {
            return null;
        }
        String normalized = compactToken(categories.getCommitment());
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "fulltime" -> EmploymentType.FULL_TIME;
            case "parttime" -> EmploymentType.PART_TIME;
            case "contract", "contractor" -> EmploymentType.CONTRACT;
            case "intern", "internship" -> EmploymentType.INTERNSHIP;
            default -> null;
        };
    }

    private WorkMode mapWorkMode(String workplaceType) {
        String normalized = compactToken(workplaceType);
        if (normalized == null || "unspecified".equals(normalized)) {
            return null;
        }
        return switch (normalized) {
            case "remote" -> WorkMode.REMOTE;
            case "hybrid" -> WorkMode.HYBRID;
            case "onsite" -> WorkMode.ON_SITE;
            default -> null;
        };
    }

    private String formatSalary(LeverJobClient.LeverPosting posting) {
        LeverJobClient.LeverSalaryRange salaryRange = posting.getSalaryRange();
        if (salaryRange != null && (salaryRange.getMin() != null || salaryRange.getMax() != null)) {
            String formatted = formatNumericSalary(salaryRange);
            String interval = normalizeInterval(salaryRange.getInterval());
            return interval == null ? formatted : formatted + " / " + interval;
        }

        String salaryDescription = normalizeWhitespace(posting.getSalaryDescriptionPlain());
        if (salaryDescription == null) {
            return null;
        }
        return salaryDescription.length() <= MAX_SALARY_DESCRIPTION_LENGTH
                ? salaryDescription
                : salaryDescription.substring(0, MAX_SALARY_DESCRIPTION_LENGTH);
    }

    private String formatNumericSalary(LeverJobClient.LeverSalaryRange salaryRange) {
        BigDecimal min = salaryRange.getMin();
        BigDecimal max = salaryRange.getMax();
        String currency = normalizeOptional(salaryRange.getCurrency());

        if (min != null && max != null) {
            if (min.compareTo(max) == 0) {
                return amountWithCurrency(currency, min);
            }
            return amountWithCurrency(currency, min) + " - " + formatNumber(max);
        }
        if (min != null) {
            return "From " + amountWithCurrency(currency, min);
        }
        return "Up to " + amountWithCurrency(currency, max);
    }

    private String amountWithCurrency(String currency, BigDecimal value) {
        String amount = formatNumber(value);
        return currency == null ? amount : currency + " " + amount;
    }

    private String formatNumber(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String normalizeInterval(String interval) {
        String normalized = compactToken(interval);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "year", "yearly", "annual", "annually", "annum", "peryear" -> "year";
            case "month", "monthly", "permonth" -> "month";
            case "week", "weekly", "perweek" -> "week";
            case "hour", "hourly", "perhour" -> "hour";
            default -> null;
        };
    }

    private String assembleDescription(LeverJobClient.LeverPosting posting) {
        List<String> fragments = new ArrayList<>();
        Set<String> seenContent = new LinkedHashSet<>();
        String description = normalizeOptional(posting.getDescription());

        if (description != null) {
            addHtmlFragment(fragments, seenContent, description);
        } else {
            String opening = normalizeOptional(posting.getOpening());
            String descriptionBody = normalizeOptional(posting.getDescriptionBody());
            if (opening != null || descriptionBody != null) {
                addHtmlFragment(fragments, seenContent, opening);
                addHtmlFragment(fragments, seenContent, descriptionBody);
            } else {
                String descriptionPlain = normalizeWhitespace(posting.getDescriptionPlain());
                if (descriptionPlain != null) {
                    addPlainFragment(fragments, seenContent, descriptionPlain);
                } else {
                    addPlainFragment(fragments, seenContent, posting.getOpeningPlain());
                    addPlainFragment(fragments, seenContent, posting.getDescriptionBodyPlain());
                }
            }
        }

        if (posting.getLists() != null) {
            for (LeverJobClient.LeverListSection section : posting.getLists()) {
                addListSection(fragments, seenContent, section);
            }
        }

        String additional = normalizeOptional(posting.getAdditional());
        if (additional != null) {
            addHtmlFragment(fragments, seenContent, additional);
        } else {
            addPlainFragment(fragments, seenContent, posting.getAdditionalPlain());
        }

        if (fragments.isEmpty()) {
            return null;
        }
        return Jsoup.clean(String.join("\n", fragments), Safelist.basic());
    }

    private void addListSection(
            List<String> fragments,
            Set<String> seenContent,
            LeverJobClient.LeverListSection section) {
        String content = normalizeOptional(section.getContent());
        String text = normalizeOptional(section.getText());
        if (content != null) {
            String fragment = text == null ? content : "<p><strong>" + escapeHtml(text) + "</strong></p>" + content;
            addHtmlFragment(fragments, seenContent, fragment);
        } else {
            addPlainFragment(fragments, seenContent, text);
        }
    }

    private void addHtmlFragment(List<String> fragments, Set<String> seenContent, String html) {
        String normalized = normalizeOptional(html);
        if (normalized == null) {
            return;
        }
        String key = duplicateKey(normalized);
        if (key != null && seenContent.add(key)) {
            fragments.add(normalized);
        }
    }

    private void addPlainFragment(List<String> fragments, Set<String> seenContent, String value) {
        String normalized = normalizeWhitespace(value);
        if (normalized == null || !seenContent.add(normalized)) {
            return;
        }
        fragments.add("<p>" + escapeHtml(normalized) + "</p>");
    }

    private String duplicateKey(String html) {
        String text = normalizeWhitespace(Jsoup.parseBodyFragment(html).text());
        return text == null ? normalizeWhitespace(html) : text;
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String compactToken(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "")
                .replace("/", "");
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

    private String normalizeWhitespace(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private record ValidatedSource(
            String site,
            String identitySite,
            String companyName,
            LeverRegion region,
            String identityRegion) {
    }

    private record SourceValidation(List<ValidatedSource> sources, int recordsFailed) {
    }
}

package com.arclume.api.service.opportunity;

import com.arclume.api.client.TheMuseJobClient;
import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.OpportunityCategory;
import com.arclume.api.domain.WorkMode;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class TheMuseJobProvider implements OpportunityProvider {

    public static final String PROVIDER_KEY = "THE_MUSE";
    public static final String ATTRIBUTION_LABEL = "Jobs provided by The Muse";

    private static final int MAX_SOURCE_ID_LENGTH = 100;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_COMPANY_LENGTH = 200;
    private static final int MAX_LOCATION_LENGTH = 200;
    private static final int MAX_URL_LENGTH = 1000;
    private static final String CURRENT_HOST = "www.themuse.com";
    private static final String ROOT_HOST = "themuse.com";
    private static final String LEGACY_HOST = "api-v2.themuse.com";
    private static final String JOB_PATH_PREFIX = "/jobs/";
    private static final Safelist DESCRIPTION_SAFELIST = Safelist.none()
            .addTags(
                    "p", "br", "ul", "ol", "li", "strong", "b", "em", "i",
                    "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "code", "pre", "a")
            .addAttributes("a", "href", "title")
            .addProtocols("a", "href", "http", "https");

    private final TheMuseJobClient theMuseJobClient;
    private final OpportunityProviderProperties properties;

    public TheMuseJobProvider(TheMuseJobClient theMuseJobClient, OpportunityProviderProperties properties) {
        this.theMuseJobClient = theMuseJobClient;
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
        return properties.getProviders().getTheMuse().isEnabled();
    }

    @Override
    public OpportunityProviderFetchResult fetchOpportunities() {
        OpportunityProviderProperties.TheMuse theMuse = properties.getProviders().getTheMuse();
        String apiKey = normalizeOptional(theMuse.getApiKey());
        if (apiKey == null) {
            throw new OpportunityProviderException("The Muse provider requires a registered application API key", null);
        }

        List<NormalizedOpportunityRecord> records = new ArrayList<>();
        Set<String> seenSourceIds = new HashSet<>();
        int recordsFetched = 0;
        int recordsSkipped = 0;

        for (int page = 0; page < theMuse.getMaxPages(); page++) {
            TheMuseJobClient.TheMuseJobPage response = theMuseJobClient.fetchPage(page, apiKey);
            List<TheMuseJobClient.TheMuseJob> jobs = response.results();
            recordsFetched += jobs.size();

            for (TheMuseJobClient.TheMuseJob job : jobs) {
                String sourceId = sourceId(job);
                if (sourceId == null) {
                    recordsSkipped++;
                    continue;
                }

                if (!seenSourceIds.add(sourceId)) {
                    recordsSkipped++;
                    continue;
                }

                NormalizedJobOpportunity normalized = normalize(job, sourceId);
                if (normalized == null) {
                    recordsSkipped++;
                    continue;
                }

                records.add(normalized);
            }

            if (response.pageCount() == 0 || jobs.isEmpty() || page >= response.pageCount() - 1) {
                break;
            }
        }

        return new OpportunityProviderFetchResult(recordsFetched, recordsSkipped, records);
    }

    private NormalizedJobOpportunity normalize(TheMuseJobClient.TheMuseJob job, String sourceId) {
        String title = normalizeRequired(job.getName(), MAX_TITLE_LENGTH);
        String company = normalizeCompany(job.getCompany());
        String sourceUrl = canonicalMuseUrl(job.getRefs() == null ? null : job.getRefs().getLandingPage());
        PublicationDateResult publicationDate = parsePublicationDate(job.getPublicationDate());
        if (title == null || company == null || sourceUrl == null || publicationDate.invalid()) {
            return null;
        }

        LocationMapping location = mapLocation(job.getLocations());
        return new NormalizedJobOpportunity(
                sourceId,
                sourceUrl,
                ATTRIBUTION_LABEL,
                title,
                company,
                location.location(),
                mapEmploymentType(job.getLevels()),
                location.workMode(),
                null,
                publicationDate.postedAt(),
                sanitizeHtml(job.getContents()),
                null,
                true
        );
    }

    private String sourceId(TheMuseJobClient.TheMuseJob job) {
        if (job == null || job.getId() == null || job.getId() <= 0) {
            return null;
        }
        String sourceId = String.valueOf(job.getId());
        return sourceId.length() <= MAX_SOURCE_ID_LENGTH ? sourceId : null;
    }

    private String normalizeCompany(TheMuseJobClient.TheMuseCompany company) {
        if (company == null) {
            return null;
        }
        return normalizeRequired(company.getName(), MAX_COMPANY_LENGTH);
    }

    private String canonicalMuseUrl(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }

        URI uri;
        try {
            uri = new URI(normalized);
        } catch (URISyntaxException e) {
            return null;
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        String rawPath = uri.getRawPath();
        String decodedPath = uri.getPath();
        if (scheme == null
                || !"https".equalsIgnoreCase(scheme)
                || host == null
                || uri.getRawUserInfo() != null
                || uri.getPort() != -1
                || !isValidMuseJobPath(uri, rawPath, decodedPath)) {
            return null;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!CURRENT_HOST.equals(normalizedHost)
                && !ROOT_HOST.equals(normalizedHost)
                && !LEGACY_HOST.equals(normalizedHost)) {
            return null;
        }

        String canonical = "https://" + CURRENT_HOST + rawPath;
        String rawQuery = uri.getRawQuery();
        if (rawQuery != null && !rawQuery.isBlank()) {
            canonical += "?" + rawQuery;
        }
        return canonical.length() <= MAX_URL_LENGTH ? canonical : null;
    }

    private boolean isValidMuseJobPath(URI uri, String rawPath, String decodedPath) {
        if (rawPath == null
                || decodedPath == null
                || !decodedPath.startsWith(JOB_PATH_PREFIX)
                || decodedPath.indexOf('\\') >= 0) {
            return false;
        }

        String jobPath = decodedPath.substring(JOB_PATH_PREFIX.length());
        if (jobPath.chars().noneMatch(ch -> ch != '/')) {
            return false;
        }

        if (Arrays.stream(decodedPath.split("/", -1)).anyMatch(segment -> ".".equals(segment) || "..".equals(segment))) {
            return false;
        }

        String normalizedPath = uri.normalize().getPath();
        return normalizedPath != null && normalizedPath.startsWith(JOB_PATH_PREFIX);
    }

    private LocationMapping mapLocation(List<TheMuseJobClient.TheMuseNamedValue> locations) {
        List<String> retained = retainedLocations(locations);
        if (retained.isEmpty()) {
            return new LocationMapping(null, null);
        }
        WorkMode workMode = retained.stream().allMatch(this::isExplicitRemoteMarker) ? WorkMode.REMOTE : null;
        return new LocationMapping(String.join(", ", retained), workMode);
    }

    private List<String> retainedLocations(List<TheMuseJobClient.TheMuseNamedValue> locations) {
        if (locations == null) {
            return List.of();
        }

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (TheMuseJobClient.TheMuseNamedValue location : locations) {
            if (location == null) {
                continue;
            }
            String normalized = normalizeOptional(location.getName());
            if (normalized != null && normalized.length() <= MAX_LOCATION_LENGTH) {
                unique.add(normalized);
            }
        }

        List<String> retained = new ArrayList<>();
        int currentLength = 0;
        for (String location : unique) {
            int candidateLength = retained.isEmpty()
                    ? location.length()
                    : currentLength + 2 + location.length();
            if (candidateLength <= MAX_LOCATION_LENGTH) {
                retained.add(location);
                currentLength = candidateLength;
            }
        }
        return retained;
    }

    private boolean isExplicitRemoteMarker(String location) {
        String normalized = location.trim().toLowerCase(Locale.ROOT);
        return "flexible / remote".equals(normalized)
                || "remote".equals(normalized)
                || normalized.startsWith("remote,");
    }

    private EmploymentType mapEmploymentType(List<TheMuseJobClient.TheMuseNamedValue> levels) {
        if (levels == null) {
            return null;
        }
        for (TheMuseJobClient.TheMuseNamedValue level : levels) {
            if (level != null && "Internship".equals(normalizeOptional(level.getName()))) {
                return EmploymentType.INTERNSHIP;
            }
        }
        return null;
    }

    private PublicationDateResult parsePublicationDate(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return new PublicationDateResult(null, false);
        }
        try {
            return new PublicationDateResult(Instant.parse(normalized), false);
        } catch (DateTimeException ignored) {
            // Try offset timestamps below before treating the individual job as invalid.
        }
        try {
            return new PublicationDateResult(OffsetDateTime.parse(normalized).toInstant(), false);
        } catch (DateTimeException e) {
            return new PublicationDateResult(null, true);
        }
    }

    private String sanitizeHtml(String html) {
        String normalized = normalizeOptional(html);
        if (normalized == null) {
            return null;
        }
        return normalizeOptional(Jsoup.clean(normalized, DESCRIPTION_SAFELIST));
    }

    private String normalizeRequired(String value, int maxLength) {
        String normalized = normalizeOptional(value);
        return normalized == null || normalized.length() > maxLength ? null : normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private record LocationMapping(String location, WorkMode workMode) {
    }

    private record PublicationDateResult(Instant postedAt, boolean invalid) {
    }
}

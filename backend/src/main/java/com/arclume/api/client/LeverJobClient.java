package com.arclume.api.client;

import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.config.OpportunityProviderProperties.LeverRegion;
import com.arclume.api.service.opportunity.OpportunityProviderException;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LeverJobClient {

    private static final ParameterizedTypeReference<List<LeverPosting>> POSTING_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final String globalApiUrl;
    private final String euApiUrl;
    private final int pageSize;
    private final int maxPages;

    @Autowired
    public LeverJobClient(RestClient.Builder restClientBuilder, OpportunityProviderProperties properties) {
        this(restClientBuilder, properties, true);
    }

    LeverJobClient(
            RestClient.Builder restClientBuilder,
            OpportunityProviderProperties properties,
            boolean configureTimeouts) {
        OpportunityProviderProperties.Lever lever = properties.getProviders().getLever();
        RestClient.Builder builder = restClientBuilder;
        if (configureTimeouts) {
            builder.requestFactory(createRequestFactory(lever));
        }
        this.restClient = builder.build();
        this.globalApiUrl = lever.getGlobalApiUrl();
        this.euApiUrl = lever.getEuApiUrl();
        this.pageSize = lever.getPageSize();
        this.maxPages = lever.getMaxPages();
    }

    public List<LeverPosting> fetchJobs(String site, LeverRegion region) {
        try {
            List<LeverPosting> postings = new ArrayList<>();
            Set<String> pageFingerprints = new HashSet<>();
            Set<String> seenIds = new HashSet<>();
            int skip = 0;

            for (int pageNumber = 0; pageNumber < maxPages; pageNumber++) {
                List<LeverPosting> page = fetchPage(site, region, skip);
                if (page.isEmpty()) {
                    return List.copyOf(postings);
                }

                validatePageProgress(page, pageFingerprints, seenIds);
                postings.addAll(page);

                if (page.size() < pageSize) {
                    return List.copyOf(postings);
                }
                skip += page.size();
            }

            throw new OpportunityProviderException("Failed to fetch jobs from Lever API", null);
        } catch (Exception e) {
            throw new OpportunityProviderException("Failed to fetch jobs from Lever API", e);
        }
    }

    private List<LeverPosting> fetchPage(String site, LeverRegion region, int skip) {
        List<LeverPosting> page = restClient.get()
                .uri(jobsUri(site, region, skip))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(POSTING_LIST_TYPE);
        return page == null ? List.of() : page;
    }

    private URI jobsUri(String site, LeverRegion region, int skip) {
        return UriComponentsBuilder.fromUriString(apiUrl(region))
                .pathSegment(site)
                .queryParam("mode", "json")
                .queryParam("skip", skip)
                .queryParam("limit", pageSize)
                .build()
                .encode()
                .toUri();
    }

    private String apiUrl(LeverRegion region) {
        return region == LeverRegion.EU ? euApiUrl : globalApiUrl;
    }

    private void validatePageProgress(
            List<LeverPosting> page,
            Set<String> pageFingerprints,
            Set<String> seenIds) {
        List<String> pageIds = page.stream()
                .map(LeverPosting::getId)
                .map(this::normalizeOptional)
                .filter(id -> id != null)
                .toList();

        if (pageIds.isEmpty()) {
            if (page.size() >= pageSize) {
                throw new OpportunityProviderException("Failed to fetch jobs from Lever API", null);
            }
            return;
        }

        String fingerprint = pageIds.stream().collect(Collectors.joining("|"));
        if (!pageFingerprints.add(fingerprint)) {
            throw new OpportunityProviderException("Failed to fetch jobs from Lever API", null);
        }

        int seenBefore = seenIds.size();
        seenIds.addAll(pageIds);
        if (seenIds.size() == seenBefore && page.size() >= pageSize) {
            throw new OpportunityProviderException("Failed to fetch jobs from Lever API", null);
        }
    }

    static SimpleClientHttpRequestFactory createRequestFactory(OpportunityProviderProperties.Lever lever) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(lever.getConnectionTimeout());
        factory.setReadTimeout(lever.getReadTimeout());
        return factory;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LeverPosting {
        private Object id;
        private String text;
        private LeverCategories categories;
        private String country;
        private String description;
        private String descriptionPlain;
        private String opening;
        private String openingPlain;
        private String descriptionBody;
        private String descriptionBodyPlain;
        private List<LeverListSection> lists;
        private String additional;
        private String additionalPlain;
        private String hostedUrl;
        private String applyUrl;
        private String workplaceType;
        private LeverSalaryRange salaryRange;
        private String salaryDescription;
        private String salaryDescriptionPlain;

        public String getId() {
            return id == null ? null : String.valueOf(id);
        }

        public void setId(Object id) {
            this.id = id;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public LeverCategories getCategories() {
            return categories;
        }

        public void setCategories(LeverCategories categories) {
            this.categories = categories;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getDescriptionPlain() {
            return descriptionPlain;
        }

        public void setDescriptionPlain(String descriptionPlain) {
            this.descriptionPlain = descriptionPlain;
        }

        public String getOpening() {
            return opening;
        }

        public void setOpening(String opening) {
            this.opening = opening;
        }

        public String getOpeningPlain() {
            return openingPlain;
        }

        public void setOpeningPlain(String openingPlain) {
            this.openingPlain = openingPlain;
        }

        public String getDescriptionBody() {
            return descriptionBody;
        }

        public void setDescriptionBody(String descriptionBody) {
            this.descriptionBody = descriptionBody;
        }

        public String getDescriptionBodyPlain() {
            return descriptionBodyPlain;
        }

        public void setDescriptionBodyPlain(String descriptionBodyPlain) {
            this.descriptionBodyPlain = descriptionBodyPlain;
        }

        public List<LeverListSection> getLists() {
            return lists;
        }

        public void setLists(List<LeverListSection> lists) {
            this.lists = lists;
        }

        public String getAdditional() {
            return additional;
        }

        public void setAdditional(String additional) {
            this.additional = additional;
        }

        public String getAdditionalPlain() {
            return additionalPlain;
        }

        public void setAdditionalPlain(String additionalPlain) {
            this.additionalPlain = additionalPlain;
        }

        public String getHostedUrl() {
            return hostedUrl;
        }

        public void setHostedUrl(String hostedUrl) {
            this.hostedUrl = hostedUrl;
        }

        public String getApplyUrl() {
            return applyUrl;
        }

        public void setApplyUrl(String applyUrl) {
            this.applyUrl = applyUrl;
        }

        public String getWorkplaceType() {
            return workplaceType;
        }

        public void setWorkplaceType(String workplaceType) {
            this.workplaceType = workplaceType;
        }

        public LeverSalaryRange getSalaryRange() {
            return salaryRange;
        }

        public void setSalaryRange(LeverSalaryRange salaryRange) {
            this.salaryRange = salaryRange;
        }

        public String getSalaryDescription() {
            return salaryDescription;
        }

        public void setSalaryDescription(String salaryDescription) {
            this.salaryDescription = salaryDescription;
        }

        public String getSalaryDescriptionPlain() {
            return salaryDescriptionPlain;
        }

        public void setSalaryDescriptionPlain(String salaryDescriptionPlain) {
            this.salaryDescriptionPlain = salaryDescriptionPlain;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LeverCategories {
        private String location;
        private String commitment;
        private String team;
        private String department;
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<String> allLocations;

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getCommitment() {
            return commitment;
        }

        public void setCommitment(String commitment) {
            this.commitment = commitment;
        }

        public String getTeam() {
            return team;
        }

        public void setTeam(String team) {
            this.team = team;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public List<String> getAllLocations() {
            return allLocations;
        }

        public void setAllLocations(List<String> allLocations) {
            this.allLocations = allLocations;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LeverListSection {
        private String text;
        private String content;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LeverSalaryRange {
        private String currency;
        private String interval;
        private BigDecimal min;
        private BigDecimal max;

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getInterval() {
            return interval;
        }

        public void setInterval(String interval) {
            this.interval = interval;
        }

        public BigDecimal getMin() {
            return min;
        }

        public void setMin(BigDecimal min) {
            this.min = min;
        }

        public BigDecimal getMax() {
            return max;
        }

        public void setMax(BigDecimal max) {
            this.max = max;
        }
    }
}

package com.arclume.api.client;

import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.service.opportunity.OpportunityProviderException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class TheMuseJobClient {

    public static final String FAILURE_MESSAGE = "Failed to fetch jobs from The Muse API";

    private final RestClient restClient;

    @Autowired
    public TheMuseJobClient(RestClient.Builder restClientBuilder, OpportunityProviderProperties properties) {
        this(restClientBuilder, properties, true);
    }

    TheMuseJobClient(
            RestClient.Builder restClientBuilder,
            OpportunityProviderProperties properties,
            boolean configureTimeouts) {
        OpportunityProviderProperties.TheMuse theMuse = properties.getProviders().getTheMuse();
        RestClient.Builder builder = restClientBuilder.baseUrl(theMuse.getApiUrl());
        if (configureTimeouts) {
            builder.requestFactory(createRequestFactory(theMuse));
        }
        this.restClient = builder.build();
    }

    public TheMuseJobPage fetchPage(int page, String apiKey) {
        try {
            TheMuseResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/jobs")
                            .queryParam("page", page)
                            .queryParam("descending", true)
                            .queryParam("api_key", apiKey)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(TheMuseResponse.class);
            return validate(response, page);
        } catch (OpportunityProviderException e) {
            throw e;
        } catch (Exception e) {
            throw failure(e);
        }
    }

    static SimpleClientHttpRequestFactory createRequestFactory(OpportunityProviderProperties.TheMuse theMuse) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(theMuse.getConnectionTimeout());
        factory.setReadTimeout(theMuse.getReadTimeout());
        return factory;
    }

    private TheMuseJobPage validate(TheMuseResponse response, int requestedPage) {
        if (response == null
                || response.getPage() == null
                || response.getPage() < 0
                || response.getPage() != requestedPage
                || response.getPageCount() == null
                || response.getPageCount() < 0
                || response.getResults() == null
                || !hasValidPageCountRelationship(response)) {
            throw failure(null);
        }
        return new TheMuseJobPage(response.getPage(), response.getPageCount(), response.getResults());
    }

    private OpportunityProviderException failure(Exception cause) {
        return new OpportunityProviderException(FAILURE_MESSAGE, sanitizedCause(cause));
    }

    private Throwable sanitizedCause(Exception cause) {
        return cause == null ? null : new IllegalStateException("The Muse API request failed");
    }

    private boolean hasValidPageCountRelationship(TheMuseResponse response) {
        int page = response.getPage();
        int pageCount = response.getPageCount();
        if (pageCount == 0) {
            return page == 0 && response.getResults().isEmpty();
        }
        return page < pageCount;
    }

    public record TheMuseJobPage(int page, int pageCount, List<TheMuseJob> results) {
        public TheMuseJobPage {
            results = Collections.unmodifiableList(new ArrayList<>(results));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TheMuseResponse {
        private Integer page;
        @JsonProperty("page_count")
        private Integer pageCount;
        private List<TheMuseJob> results;

        public Integer getPage() {
            return page;
        }

        public void setPage(Integer page) {
            this.page = page;
        }

        public Integer getPageCount() {
            return pageCount;
        }

        public void setPageCount(Integer pageCount) {
            this.pageCount = pageCount;
        }

        public List<TheMuseJob> getResults() {
            return results;
        }

        public void setResults(List<TheMuseJob> results) {
            this.results = results;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TheMuseJob {
        private Long id;
        private String name;
        @JsonProperty("short_name")
        private String shortName;
        private String contents;
        @JsonProperty("publication_date")
        private String publicationDate;
        private String type;
        private TheMuseCompany company;
        private List<TheMuseNamedValue> locations;
        private List<TheMuseNamedValue> levels;
        private List<TheMuseNamedValue> categories;
        private TheMuseRefs refs;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getShortName() {
            return shortName;
        }

        public void setShortName(String shortName) {
            this.shortName = shortName;
        }

        public String getContents() {
            return contents;
        }

        public void setContents(String contents) {
            this.contents = contents;
        }

        public String getPublicationDate() {
            return publicationDate;
        }

        public void setPublicationDate(String publicationDate) {
            this.publicationDate = publicationDate;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public TheMuseCompany getCompany() {
            return company;
        }

        public void setCompany(TheMuseCompany company) {
            this.company = company;
        }

        public List<TheMuseNamedValue> getLocations() {
            return locations;
        }

        public void setLocations(List<TheMuseNamedValue> locations) {
            this.locations = locations;
        }

        public List<TheMuseNamedValue> getLevels() {
            return levels;
        }

        public void setLevels(List<TheMuseNamedValue> levels) {
            this.levels = levels;
        }

        public List<TheMuseNamedValue> getCategories() {
            return categories;
        }

        public void setCategories(List<TheMuseNamedValue> categories) {
            this.categories = categories;
        }

        public TheMuseRefs getRefs() {
            return refs;
        }

        public void setRefs(TheMuseRefs refs) {
            this.refs = refs;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TheMuseCompany {
        private Long id;
        private String name;
        @JsonProperty("short_name")
        private String shortName;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getShortName() {
            return shortName;
        }

        public void setShortName(String shortName) {
            this.shortName = shortName;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TheMuseRefs {
        @JsonProperty("landing_page")
        private String landingPage;

        public String getLandingPage() {
            return landingPage;
        }

        public void setLandingPage(String landingPage) {
            this.landingPage = landingPage;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TheMuseNamedValue {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}

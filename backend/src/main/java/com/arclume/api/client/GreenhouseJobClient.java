package com.arclume.api.client;

import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.service.opportunity.OpportunityProviderException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;

@Component
public class GreenhouseJobClient {

    private final RestClient restClient;
    private final String apiUrl;

    @Autowired
    public GreenhouseJobClient(RestClient.Builder restClientBuilder, OpportunityProviderProperties properties) {
        this(restClientBuilder, properties, true);
    }

    GreenhouseJobClient(
            RestClient.Builder restClientBuilder,
            OpportunityProviderProperties properties,
            boolean configureTimeouts) {
        OpportunityProviderProperties.Greenhouse greenhouse = properties.getProviders().getGreenhouse();
        RestClient.Builder builder = restClientBuilder;
        if (configureTimeouts) {
            builder.requestFactory(createRequestFactory(greenhouse));
        }
        this.restClient = builder.build();
        this.apiUrl = greenhouse.getApiUrl();
    }

    public List<GreenhouseJob> fetchJobs(String boardToken) {
        try {
            GreenhouseResponse response = restClient.get()
                    .uri(jobsUri(boardToken))
                    .retrieve()
                    .body(GreenhouseResponse.class);

            if (response != null && response.getJobs() != null) {
                return response.getJobs();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            throw new OpportunityProviderException("Failed to fetch jobs from Greenhouse API", e);
        }
    }

    private URI jobsUri(String boardToken) {
        return UriComponentsBuilder.fromUriString(apiUrl)
                .pathSegment(boardToken, "jobs")
                .queryParam("content", "true")
                .build()
                .encode()
                .toUri();
    }

    static SimpleClientHttpRequestFactory createRequestFactory(OpportunityProviderProperties.Greenhouse greenhouse) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(greenhouse.getConnectionTimeout());
        factory.setReadTimeout(greenhouse.getReadTimeout());
        return factory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GreenhouseResponse {
        private List<GreenhouseJob> jobs;
        private GreenhouseMeta meta;

        public List<GreenhouseJob> getJobs() {
            return jobs;
        }

        public void setJobs(List<GreenhouseJob> jobs) {
            this.jobs = jobs;
        }

        public GreenhouseMeta getMeta() {
            return meta;
        }

        public void setMeta(GreenhouseMeta meta) {
            this.meta = meta;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GreenhouseMeta {
        private Integer total;

        public Integer getTotal() {
            return total;
        }

        public void setTotal(Integer total) {
            this.total = total;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GreenhouseJob {
        private Object id;
        @JsonProperty("internal_job_id")
        private Object internalJobId;
        private String title;
        @JsonProperty("updated_at")
        private String updatedAt;
        private GreenhouseLocation location;
        @JsonProperty("absolute_url")
        private String absoluteUrl;
        private String language;
        private String content;
        private List<GreenhouseEntity> departments;
        private List<GreenhouseEntity> offices;

        public String getId() {
            return id == null ? null : String.valueOf(id);
        }

        public void setId(Object id) {
            this.id = id;
        }

        public String getInternalJobId() {
            return internalJobId == null ? null : String.valueOf(internalJobId);
        }

        public void setInternalJobId(Object internalJobId) {
            this.internalJobId = internalJobId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }

        public GreenhouseLocation getLocation() {
            return location;
        }

        public void setLocation(GreenhouseLocation location) {
            this.location = location;
        }

        public String getAbsoluteUrl() {
            return absoluteUrl;
        }

        public void setAbsoluteUrl(String absoluteUrl) {
            this.absoluteUrl = absoluteUrl;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public List<GreenhouseEntity> getDepartments() {
            return departments;
        }

        public void setDepartments(List<GreenhouseEntity> departments) {
            this.departments = departments;
        }

        public List<GreenhouseEntity> getOffices() {
            return offices;
        }

        public void setOffices(List<GreenhouseEntity> offices) {
            this.offices = offices;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GreenhouseLocation {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GreenhouseEntity {
        private Object id;
        private String name;

        public String getId() {
            return id == null ? null : String.valueOf(id);
        }

        public void setId(Object id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}

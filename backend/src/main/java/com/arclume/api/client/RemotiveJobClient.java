package com.arclume.api.client;

import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.service.opportunity.OpportunityProviderException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

@Component
public class RemotiveJobClient {

    private final RestClient restClient;

    @Autowired
    public RemotiveJobClient(RestClient.Builder restClientBuilder, OpportunityProviderProperties properties) {
        this(restClientBuilder, properties, true);
    }

    RemotiveJobClient(
            RestClient.Builder restClientBuilder,
            OpportunityProviderProperties properties,
            boolean configureTimeouts) {
        OpportunityProviderProperties.Remotive remotive = properties.getProviders().getRemotive();

        RestClient.Builder builder = restClientBuilder.baseUrl(remotive.getApiUrl());
        if (configureTimeouts) {
            builder.requestFactory(createRequestFactory(remotive));
        }

        this.restClient = builder.build();
    }

    public List<RemotiveJob> fetchJobs() {
        try {
            RemotiveResponse response = restClient.get()
                    .uri("")
                    .retrieve()
                    .body(RemotiveResponse.class);

            if (response != null && response.getJobs() != null) {
                return response.getJobs();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            throw new OpportunityProviderException("Failed to fetch jobs from Remotive API", e);
        }
    }

    static SimpleClientHttpRequestFactory createRequestFactory(OpportunityProviderProperties.Remotive remotive) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(remotive.getConnectionTimeout());
        factory.setReadTimeout(remotive.getReadTimeout());
        return factory;
    }

    public static class RemotiveResponse {
        @JsonProperty("jobs")
        private List<RemotiveJob> jobs;

        public List<RemotiveJob> getJobs() {
            return jobs;
        }

        public void setJobs(List<RemotiveJob> jobs) {
            this.jobs = jobs;
        }
    }

    public static class RemotiveJob {
        private String id;
        private String url;
        private String title;
        @JsonProperty("company_name")
        private String companyName;
        @JsonProperty("job_type")
        private String jobType;
        @JsonProperty("publication_date")
        private String publicationDate;
        @JsonProperty("candidate_required_location")
        private String candidateRequiredLocation;
        private String salary;
        private String description;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }

        public String getJobType() {
            return jobType;
        }

        public void setJobType(String jobType) {
            this.jobType = jobType;
        }

        public String getPublicationDate() {
            return publicationDate;
        }

        public void setPublicationDate(String publicationDate) {
            this.publicationDate = publicationDate;
        }

        public String getCandidateRequiredLocation() {
            return candidateRequiredLocation;
        }

        public void setCandidateRequiredLocation(String candidateRequiredLocation) {
            this.candidateRequiredLocation = candidateRequiredLocation;
        }

        public String getSalary() {
            return salary;
        }

        public void setSalary(String salary) {
            this.salary = salary;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}

package com.arclume.api.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

@Component
public class JobClient {

    private final RestClient restClient;

    public JobClient(
            @Value("${app.jobs.sync.api-url:https://remotive.com/api/remote-jobs}") String apiUrl,
            @Value("${app.jobs.sync.timeout-ms:5000}") int timeoutMs) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(apiUrl)
                .build();
    }

    public List<RemotiveJob> fetchJobs() {
        try {
            RemotiveResponse response = restClient.get()
                    .retrieve()
                    .body(RemotiveResponse.class);

            if (response != null && response.getJobs() != null) {
                return response.getJobs();
            }
        } catch (Exception e) {
            // Log the error and return empty list or propagate a custom exception
            // Since we need to handle failures safely, we return an empty list or let JobSyncService know.
            throw new RuntimeException("Failed to fetch jobs from Remotive API: " + e.getMessage(), e);
        }
        return Collections.emptyList();
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

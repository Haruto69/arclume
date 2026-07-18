package com.arclume.api.client;

import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.service.opportunity.OpportunityProviderException;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Component
public class JobicyJobClient {

    private final RestClient restClient;
    private final int count;

    @Autowired
    public JobicyJobClient(RestClient.Builder restClientBuilder, OpportunityProviderProperties properties) {
        this(restClientBuilder, properties, true);
    }

    JobicyJobClient(
            RestClient.Builder restClientBuilder,
            OpportunityProviderProperties properties,
            boolean configureTimeouts) {
        OpportunityProviderProperties.Jobicy jobicy = properties.getProviders().getJobicy();

        RestClient.Builder builder = restClientBuilder.baseUrl(jobicy.getApiUrl());
        if (configureTimeouts) {
            builder.requestFactory(createRequestFactory(jobicy));
        }

        this.restClient = builder.build();
        this.count = jobicy.getCount();
    }

    public List<JobicyJob> fetchJobs() {
        try {
            JobicyResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.queryParam("count", count).build())
                    .retrieve()
                    .body(JobicyResponse.class);

            if (response != null && response.getJobs() != null) {
                return response.getJobs();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            throw new OpportunityProviderException("Failed to fetch jobs from Jobicy API", e);
        }
    }

    static SimpleClientHttpRequestFactory createRequestFactory(OpportunityProviderProperties.Jobicy jobicy) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(jobicy.getConnectionTimeout());
        factory.setReadTimeout(jobicy.getReadTimeout());
        return factory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JobicyResponse {
        @JsonProperty("jobs")
        private List<JobicyJob> jobs;

        public List<JobicyJob> getJobs() {
            return jobs;
        }

        public void setJobs(List<JobicyJob> jobs) {
            this.jobs = jobs;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JobicyJob {
        private Object id;
        private String url;
        private String jobTitle;
        private String companyName;
        private String companyLogo;
        private String jobIndustry;
        private String jobType;
        private String jobGeo;
        private String jobLevel;
        private String jobExcerpt;
        private String jobDescription;
        private String pubDate;
        @JsonAlias({"annualSalaryMin"})
        private BigDecimal salaryMin;
        @JsonAlias({"annualSalaryMax"})
        private BigDecimal salaryMax;
        private String salaryCurrency;
        private String salaryPeriod;

        public String getId() {
            return id == null ? null : String.valueOf(id);
        }

        public void setId(Object id) {
            this.id = id;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getJobTitle() {
            return jobTitle;
        }

        public void setJobTitle(String jobTitle) {
            this.jobTitle = jobTitle;
        }

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }

        public String getCompanyLogo() {
            return companyLogo;
        }

        public void setCompanyLogo(String companyLogo) {
            this.companyLogo = companyLogo;
        }

        public String getJobIndustry() {
            return jobIndustry;
        }

        public void setJobIndustry(String jobIndustry) {
            this.jobIndustry = jobIndustry;
        }

        public String getJobType() {
            return jobType;
        }

        public void setJobType(String jobType) {
            this.jobType = jobType;
        }

        public String getJobGeo() {
            return jobGeo;
        }

        public void setJobGeo(String jobGeo) {
            this.jobGeo = jobGeo;
        }

        public String getJobLevel() {
            return jobLevel;
        }

        public void setJobLevel(String jobLevel) {
            this.jobLevel = jobLevel;
        }

        public String getJobExcerpt() {
            return jobExcerpt;
        }

        public void setJobExcerpt(String jobExcerpt) {
            this.jobExcerpt = jobExcerpt;
        }

        public String getJobDescription() {
            return jobDescription;
        }

        public void setJobDescription(String jobDescription) {
            this.jobDescription = jobDescription;
        }

        public String getPubDate() {
            return pubDate;
        }

        public void setPubDate(String pubDate) {
            this.pubDate = pubDate;
        }

        public BigDecimal getSalaryMin() {
            return salaryMin;
        }

        public void setSalaryMin(BigDecimal salaryMin) {
            this.salaryMin = salaryMin;
        }

        public BigDecimal getSalaryMax() {
            return salaryMax;
        }

        public void setSalaryMax(BigDecimal salaryMax) {
            this.salaryMax = salaryMax;
        }

        public String getSalaryCurrency() {
            return salaryCurrency;
        }

        public void setSalaryCurrency(String salaryCurrency) {
            this.salaryCurrency = salaryCurrency;
        }

        public String getSalaryPeriod() {
            return salaryPeriod;
        }

        public void setSalaryPeriod(String salaryPeriod) {
            this.salaryPeriod = salaryPeriod;
        }
    }
}

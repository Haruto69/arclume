package com.arclume.api.client;

import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.service.opportunity.OpportunityProviderException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class CodeforcesContestClient {

    public static final String FAILURE_MESSAGE = "Failed to fetch contests from Codeforces API";

    private final RestClient restClient;

    @Autowired
    public CodeforcesContestClient(RestClient.Builder restClientBuilder, OpportunityProviderProperties properties) {
        this(restClientBuilder, properties, true);
    }

    CodeforcesContestClient(
            RestClient.Builder restClientBuilder,
            OpportunityProviderProperties properties,
            boolean configureTimeouts) {
        OpportunityProviderProperties.Codeforces codeforces = properties.getProviders().getCodeforces();
        RestClient.Builder builder = restClientBuilder.baseUrl(codeforces.getApiUrl());
        if (configureTimeouts) {
            builder.requestFactory(createRequestFactory(codeforces));
        }
        this.restClient = builder.build();
    }

    public List<CodeforcesContest> fetchContests() {
        CodeforcesResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/contest.list")
                            .queryParam("gym", "false")
                            .queryParam("lang", "en")
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(CodeforcesResponse.class);
        } catch (Exception e) {
            throw failure(e);
        }

        if (response == null || response.getStatus() == null) {
            throw failure(null);
        }
        if (!"OK".equals(response.getStatus())) {
            throw failure(null);
        }
        if (response.getResult() == null) {
            throw failure(null);
        }
        return Collections.unmodifiableList(new ArrayList<>(response.getResult()));
    }

    static SimpleClientHttpRequestFactory createRequestFactory(
            OpportunityProviderProperties.Codeforces codeforces) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(codeforces.getConnectionTimeout());
        factory.setReadTimeout(codeforces.getReadTimeout());
        return factory;
    }

    private OpportunityProviderException failure(Exception cause) {
        return new OpportunityProviderException(FAILURE_MESSAGE, cause);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodeforcesResponse {
        private String status;
        private String comment;
        private List<CodeforcesContest> result;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public List<CodeforcesContest> getResult() {
            return result;
        }

        public void setResult(List<CodeforcesContest> result) {
            this.result = result;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodeforcesContest {
        private Long id;
        private String name;
        private String type;
        private String phase;
        private Boolean frozen;
        private Long durationSeconds;
        private Long startTimeSeconds;
        private Long relativeTimeSeconds;
        private String preparedBy;
        private String websiteUrl;
        private String description;
        private Integer difficulty;
        private String kind;
        private String icpcRegion;
        private String country;
        private String city;
        private String season;

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

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPhase() {
            return phase;
        }

        public void setPhase(String phase) {
            this.phase = phase;
        }

        public Boolean getFrozen() {
            return frozen;
        }

        public void setFrozen(Boolean frozen) {
            this.frozen = frozen;
        }

        public Long getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(Long durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        public Long getStartTimeSeconds() {
            return startTimeSeconds;
        }

        public void setStartTimeSeconds(Long startTimeSeconds) {
            this.startTimeSeconds = startTimeSeconds;
        }

        public Long getRelativeTimeSeconds() {
            return relativeTimeSeconds;
        }

        public void setRelativeTimeSeconds(Long relativeTimeSeconds) {
            this.relativeTimeSeconds = relativeTimeSeconds;
        }

        public String getPreparedBy() {
            return preparedBy;
        }

        public void setPreparedBy(String preparedBy) {
            this.preparedBy = preparedBy;
        }

        public String getWebsiteUrl() {
            return websiteUrl;
        }

        public void setWebsiteUrl(String websiteUrl) {
            this.websiteUrl = websiteUrl;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Integer getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(Integer difficulty) {
            this.difficulty = difficulty;
        }

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getIcpcRegion() {
            return icpcRegion;
        }

        public void setIcpcRegion(String icpcRegion) {
            this.icpcRegion = icpcRegion;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getSeason() {
            return season;
        }

        public void setSeason(String season) {
            this.season = season;
        }
    }
}

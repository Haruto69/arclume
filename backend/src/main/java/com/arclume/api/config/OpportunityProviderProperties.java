package com.arclume.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.opportunity")
public class OpportunityProviderProperties {

    private Providers providers = new Providers();

    public Providers getProviders() {
        return providers;
    }

    public void setProviders(Providers providers) {
        this.providers = providers;
    }

    public static class Providers {
        private Remotive remotive = new Remotive();
        private Jobicy jobicy = new Jobicy();

        public Remotive getRemotive() {
            return remotive;
        }

        public void setRemotive(Remotive remotive) {
            this.remotive = remotive;
        }

        public Jobicy getJobicy() {
            return jobicy;
        }

        public void setJobicy(Jobicy jobicy) {
            this.jobicy = jobicy;
        }
    }

    public static class Remotive {
        private boolean enabled = false;
        private String apiUrl = "https://remotive.com/api/remote-jobs";
        private Duration connectionTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public Duration getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }

    public static class Jobicy {
        private static final int DEFAULT_COUNT = 50;
        private static final int MAX_COUNT = 100;

        private boolean enabled = false;
        private String apiUrl = "https://jobicy.com/api/v2/remote-jobs";
        private int count = DEFAULT_COUNT;
        private Duration connectionTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            if (count <= 0) {
                this.count = DEFAULT_COUNT;
                return;
            }
            this.count = Math.min(count, MAX_COUNT);
        }

        public Duration getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }
}

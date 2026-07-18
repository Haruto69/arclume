package com.arclume.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.opportunity")
public class OpportunityProviderProperties {

    private Providers providers = new Providers();

    public Providers getProviders() {
        return providers;
    }

    public void setProviders(Providers providers) {
        this.providers = providers == null ? new Providers() : providers;
    }

    public static class Providers {
        private Remotive remotive = new Remotive();
        private Jobicy jobicy = new Jobicy();
        private Greenhouse greenhouse = new Greenhouse();
        private Lever lever = new Lever();
        private Codeforces codeforces = new Codeforces();
        private TheMuse theMuse = new TheMuse();

        public Remotive getRemotive() {
            return remotive;
        }

        public void setRemotive(Remotive remotive) {
            this.remotive = remotive == null ? new Remotive() : remotive;
        }

        public Jobicy getJobicy() {
            return jobicy;
        }

        public void setJobicy(Jobicy jobicy) {
            this.jobicy = jobicy == null ? new Jobicy() : jobicy;
        }

        public Greenhouse getGreenhouse() {
            return greenhouse;
        }

        public void setGreenhouse(Greenhouse greenhouse) {
            this.greenhouse = greenhouse == null ? new Greenhouse() : greenhouse;
        }

        public Lever getLever() {
            return lever;
        }

        public void setLever(Lever lever) {
            this.lever = lever == null ? new Lever() : lever;
        }

        public Codeforces getCodeforces() {
            return codeforces;
        }

        public void setCodeforces(Codeforces codeforces) {
            this.codeforces = codeforces == null ? new Codeforces() : codeforces;
        }

        public TheMuse getTheMuse() {
            return theMuse;
        }

        public void setTheMuse(TheMuse theMuse) {
            this.theMuse = theMuse == null ? new TheMuse() : theMuse;
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

    public static class Greenhouse {
        private boolean enabled = false;
        private String apiUrl = "https://boards-api.greenhouse.io/v1/boards";
        private Duration connectionTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(5);
        private List<GreenhouseSource> sources = new ArrayList<>();

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

        public List<GreenhouseSource> getSources() {
            return sources;
        }

        public void setSources(List<GreenhouseSource> sources) {
            this.sources = sources == null ? new ArrayList<>() : new ArrayList<>(sources);
        }
    }

    public static class GreenhouseSource {
        private boolean enabled = false;
        private String boardToken;
        private String companyName;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBoardToken() {
            return boardToken;
        }

        public void setBoardToken(String boardToken) {
            this.boardToken = boardToken;
        }

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }
    }

    public static class Lever {
        private static final int DEFAULT_PAGE_SIZE = 100;
        private static final int MAX_PAGE_SIZE = 100;
        private static final int DEFAULT_MAX_PAGES = 20;
        private static final int MAX_MAX_PAGES = 20;

        private boolean enabled = false;
        private String globalApiUrl = "https://api.lever.co/v0/postings";
        private String euApiUrl = "https://api.eu.lever.co/v0/postings";
        private int pageSize = DEFAULT_PAGE_SIZE;
        private int maxPages = DEFAULT_MAX_PAGES;
        private Duration connectionTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(5);
        private List<LeverSource> sources = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getGlobalApiUrl() {
            return globalApiUrl;
        }

        public void setGlobalApiUrl(String globalApiUrl) {
            this.globalApiUrl = globalApiUrl;
        }

        public String getEuApiUrl() {
            return euApiUrl;
        }

        public void setEuApiUrl(String euApiUrl) {
            this.euApiUrl = euApiUrl;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            if (pageSize <= 0) {
                this.pageSize = DEFAULT_PAGE_SIZE;
                return;
            }
            this.pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
        }

        public int getMaxPages() {
            return maxPages;
        }

        public void setMaxPages(int maxPages) {
            if (maxPages <= 0) {
                this.maxPages = DEFAULT_MAX_PAGES;
                return;
            }
            this.maxPages = Math.min(maxPages, MAX_MAX_PAGES);
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

        public List<LeverSource> getSources() {
            return sources;
        }

        public void setSources(List<LeverSource> sources) {
            this.sources = sources == null ? new ArrayList<>() : new ArrayList<>(sources);
        }
    }

    public static class LeverSource {
        private boolean enabled = false;
        private String site;
        private String companyName;
        private LeverRegion region = LeverRegion.GLOBAL;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSite() {
            return site;
        }

        public void setSite(String site) {
            this.site = site;
        }

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }

        public LeverRegion getRegion() {
            return region;
        }

        public void setRegion(LeverRegion region) {
            this.region = region == null ? LeverRegion.GLOBAL : region;
        }
    }

    public enum LeverRegion {
        GLOBAL,
        EU
    }
    public static class Codeforces {
        private static final int DEFAULT_PAST_RETENTION_DAYS = 14;
        private static final int MAX_PAST_RETENTION_DAYS = 365;

        private boolean enabled = false;
        private String apiUrl = "https://codeforces.com/api";
        private int pastRetentionDays = DEFAULT_PAST_RETENTION_DAYS;
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

        public int getPastRetentionDays() {
            return pastRetentionDays;
        }

        public void setPastRetentionDays(int pastRetentionDays) {
            if (pastRetentionDays < 0) {
                this.pastRetentionDays = DEFAULT_PAST_RETENTION_DAYS;
                return;
            }
            this.pastRetentionDays = Math.min(pastRetentionDays, MAX_PAST_RETENTION_DAYS);
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

    public static class TheMuse {
        private static final int DEFAULT_MAX_PAGES = 10;
        private static final int MAX_MAX_PAGES = 50;
        private static final String DEFAULT_API_URL = "https://www.themuse.com/api/public";
        private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

        private boolean enabled = false;
        private String apiUrl = DEFAULT_API_URL;
        private String apiKey = "";
        private int maxPages = DEFAULT_MAX_PAGES;
        private Duration connectionTimeout = DEFAULT_TIMEOUT;
        private Duration readTimeout = DEFAULT_TIMEOUT;

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
            this.apiUrl = apiUrl == null || apiUrl.trim().isEmpty() ? DEFAULT_API_URL : apiUrl.trim();
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey == null ? "" : apiKey;
        }

        public int getMaxPages() {
            return maxPages;
        }

        public void setMaxPages(int maxPages) {
            if (maxPages <= 0) {
                this.maxPages = DEFAULT_MAX_PAGES;
                return;
            }
            this.maxPages = Math.min(maxPages, MAX_MAX_PAGES);
        }

        public Duration getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = connectionTimeout == null ? DEFAULT_TIMEOUT : connectionTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout == null ? DEFAULT_TIMEOUT : readTimeout;
        }
    }
}

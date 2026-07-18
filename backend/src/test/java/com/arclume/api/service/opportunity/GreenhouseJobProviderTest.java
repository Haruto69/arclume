package com.arclume.api.service.opportunity;

import com.arclume.api.client.GreenhouseJobClient;
import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.domain.OpportunityCategory;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GreenhouseJobProviderTest {

    private final StubGreenhouseJobClient client = new StubGreenhouseJobClient();
    private final OpportunityProviderProperties properties = new OpportunityProviderProperties();
    private final GreenhouseJobProvider provider = new GreenhouseJobProvider(client, properties);

    @Test
    void providerMetadataAndDefaultActivationAreStable() {
        assertThat(provider.providerKey()).isEqualTo("GREENHOUSE");
        assertThat(provider.category()).isEqualTo(OpportunityCategory.JOB);
        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    void noEnabledSourcesCauseSafeProviderFailureWithoutHttp() {
        assertThatThrownBy(provider::fetchOpportunities)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Greenhouse provider requires at least one enabled source");
        assertThat(client.fetchedBoardTokens()).isEmpty();
    }

    @Test
    void disabledSourceDoesNotCallClient() {
        properties.getProviders().getGreenhouse().setSources(List.of(source(false, "disabled-board", "Disabled Co")));

        assertThatThrownBy(provider::fetchOpportunities)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Greenhouse provider requires at least one enabled source");
        assertThat(client.fetchedBoardTokens()).isEmpty();
    }

    @Test
    void normalizesValidJobWithConfiguredCompanyCompositeIdentityAndSanitizedHtml() {
        properties.getProviders().getGreenhouse().setSources(List.of(source(true, "Approved_Board", "Approved Company")));
        GreenhouseJobClient.GreenhouseJob job = job("98765", "Platform Engineer", "https://boards.greenhouse.io/fake/jobs/98765");
        job.setLocation(location("Remote"));
        job.setContent("&amp;lt;p&amp;gt;Build APIs&amp;lt;/p&amp;gt;&amp;lt;script&amp;gt;alert(1)&amp;lt;/script&amp;gt;"
                + "&amp;lt;a href=&amp;quot;javascript:alert(1)&amp;quot; onclick=&amp;quot;x&amp;quot;&amp;gt;link&amp;lt;/a&amp;gt;");
        job.setUpdatedAt("2026-07-18T10:15:30Z");
        job.setLanguage("en");
        client.returnJobs("Approved_Board", List.of(job));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(1);
        assertThat(result.recordsSkipped()).isZero();
        assertThat(result.recordsFailed()).isZero();
        assertThat(result.records()).singleElement().satisfies(record -> {
            assertThat(record).isInstanceOf(NormalizedJobOpportunity.class);
            NormalizedJobOpportunity normalized = (NormalizedJobOpportunity) record;
            assertThat(normalized.sourceId()).isEqualTo("approved_board:98765");
            assertThat(normalized.sourceUrl()).isEqualTo("https://boards.greenhouse.io/fake/jobs/98765");
            assertThat(normalized.attributionLabel()).isEqualTo("Job listing via Greenhouse");
            assertThat(normalized.title()).isEqualTo("Platform Engineer");
            assertThat(normalized.company()).isEqualTo("Approved Company");
            assertThat(normalized.location()).isEqualTo("Remote");
            assertThat(normalized.employmentType()).isNull();
            assertThat(normalized.workMode()).isNull();
            assertThat(normalized.salaryRange()).isNull();
            assertThat(normalized.postedAt()).isNull();
            assertThat(normalized.description()).contains("<p>Build APIs</p>");
            assertThat(normalized.description()).doesNotContain("script", "onclick", "javascript:");
            assertThat(normalized.requirements()).isEmpty();
            assertThat(normalized.active()).isTrue();
        });
    }

    @Test
    void skipsJobsMissingRequiredFieldsAndDuplicateCompositeIds() {
        properties.getProviders().getGreenhouse().setSources(List.of(source(true, "approved-board", "Approved Company")));
        GreenhouseJobClient.GreenhouseJob missingId = job(" ", "Developer", "https://boards.greenhouse.io/fake/jobs/missing-id");
        GreenhouseJobClient.GreenhouseJob missingUrl = job("missing-url", "Developer", " ");
        GreenhouseJobClient.GreenhouseJob missingTitle = job("missing-title", " ", "https://boards.greenhouse.io/fake/jobs/missing-title");
        GreenhouseJobClient.GreenhouseJob valid = job("valid", "Developer", "https://boards.greenhouse.io/fake/jobs/valid");
        GreenhouseJobClient.GreenhouseJob duplicate = job("valid", "Duplicate Developer", "https://boards.greenhouse.io/fake/jobs/valid-duplicate");
        client.returnJobs("approved-board", List.of(missingId, missingUrl, missingTitle, valid, duplicate));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(5);
        assertThat(result.recordsSkipped()).isEqualTo(4);
        assertThat(result.recordsFailed()).isZero();
        assertThat(result.records()).singleElement()
                .satisfies(record -> assertThat(((NormalizedJobOpportunity) record).sourceId())
                        .isEqualTo("approved-board:valid"));
    }

    @Test
    void invalidSourceConfigurationCountsAsSourceFailureWhenAnotherBoardSucceeds() {
        properties.getProviders().getGreenhouse().setSources(List.of(
                source(true, "invalid source", "Bad Source Co"),
                source(true, "valid-source", "Valid Source Co")
        ));
        client.returnJobs("valid-source", List.of(job("green-1", "Developer", "https://boards.greenhouse.io/fake/jobs/green-1")));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(1);
        assertThat(result.recordsSkipped()).isZero();
        assertThat(result.recordsFailed()).isEqualTo(1);
        assertThat(result.records()).hasSize(1);
        assertThat(client.fetchedBoardTokens()).containsExactly("valid-source");
    }

    @Test
    void missingConfiguredCompanyIsSourceConfigurationFailure() {
        properties.getProviders().getGreenhouse().setSources(List.of(
                source(true, "missing-company", " "),
                source(true, "valid-company", "Valid Company")
        ));
        client.returnJobs("valid-company", List.of(job("green-1", "Developer", "https://boards.greenhouse.io/fake/jobs/green-1")));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFailed()).isEqualTo(1);
        assertThat(result.records()).singleElement()
                .satisfies(record -> assertThat(((NormalizedJobOpportunity) record).company()).isEqualTo("Valid Company"));
        assertThat(client.fetchedBoardTokens()).containsExactly("valid-company");
    }

    @Test
    void oneSuccessfulBoardPlusOneFailedBoardReturnsPartialFetchCounters() {
        properties.getProviders().getGreenhouse().setSources(List.of(
                source(true, "good-board", "Good Company"),
                source(true, "failing-board", "Failing Company")
        ));
        client.returnJobs("good-board", List.of(job("green-1", "Developer", "https://boards.greenhouse.io/fake/jobs/green-1")));
        client.failBoard("failing-board");

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(1);
        assertThat(result.recordsSkipped()).isZero();
        assertThat(result.recordsFailed()).isEqualTo(1);
        assertThat(result.records()).hasSize(1);
        assertThat(client.fetchedBoardTokens()).containsExactly("good-board", "failing-board");
    }

    @Test
    void everyEnabledBoardFailingProducesProviderWideSafeException() {
        properties.getProviders().getGreenhouse().setSources(List.of(source(true, "failing-board", "Failing Company")));
        client.failBoard("failing-board");

        assertThatThrownBy(provider::fetchOpportunities)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Greenhouse provider failed to fetch configured sources")
                .hasMessageNotContaining("failing-board");
        assertThat(client.fetchedBoardTokens()).containsExactly("failing-board");
    }

    private OpportunityProviderProperties.GreenhouseSource source(boolean enabled, String boardToken, String companyName) {
        OpportunityProviderProperties.GreenhouseSource source = new OpportunityProviderProperties.GreenhouseSource();
        source.setEnabled(enabled);
        source.setBoardToken(boardToken);
        source.setCompanyName(companyName);
        return source;
    }

    private GreenhouseJobClient.GreenhouseJob job(String id, String title, String url) {
        GreenhouseJobClient.GreenhouseJob job = new GreenhouseJobClient.GreenhouseJob();
        job.setId(id);
        job.setTitle(title);
        job.setAbsoluteUrl(url);
        return job;
    }

    private GreenhouseJobClient.GreenhouseLocation location(String name) {
        GreenhouseJobClient.GreenhouseLocation location = new GreenhouseJobClient.GreenhouseLocation();
        location.setName(name);
        return location;
    }

    private static class StubGreenhouseJobClient extends GreenhouseJobClient {
        private final Map<String, List<GreenhouseJob>> jobsByBoard = new HashMap<>();
        private final Set<String> failingBoards = new HashSet<>();
        private final LinkedHashSet<String> fetchedBoardTokens = new LinkedHashSet<>();

        StubGreenhouseJobClient() {
            super(RestClient.builder(), new OpportunityProviderProperties());
        }

        void returnJobs(String boardToken, List<GreenhouseJob> jobs) {
            jobsByBoard.put(boardToken, jobs);
        }

        void failBoard(String boardToken) {
            failingBoards.add(boardToken);
        }

        Set<String> fetchedBoardTokens() {
            return fetchedBoardTokens;
        }

        @Override
        public List<GreenhouseJob> fetchJobs(String boardToken) {
            fetchedBoardTokens.add(boardToken);
            if (failingBoards.contains(boardToken)) {
                throw new OpportunityProviderException("Greenhouse timeout", null);
            }
            return jobsByBoard.getOrDefault(boardToken, List.of());
        }
    }
}

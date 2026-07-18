package com.arclume.api.client;

import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.config.OpportunityProviderProperties.LeverRegion;
import com.arclume.api.service.opportunity.OpportunityProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LeverJobClientTest {

    private static final String GLOBAL_API_URL = "https://lever-global.test/v0/postings";
    private static final String EU_API_URL = "https://lever-eu.test/v0/postings";

    @Test
    void fetchJobsUsesGlobalBaseUrlJsonModePaginationAndAcceptHeader() {
        ClientFixture fixture = clientFixture(2, 3);
        fixture.server().expect(requestTo(GLOBAL_API_URL + "/approved_site?mode=json&skip=0&limit=2"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("""
                        [
                          {
                            "id": "posting-1",
                            "text": "Backend Engineer",
                            "categories": {
                              "location": "Remote",
                              "commitment": "Full-time",
                              "team": "Engineering",
                              "department": "Platform",
                              "allLocations": ["Remote", "London"]
                            },
                            "country": "US",
                            "description": "<p>Build services</p>",
                            "descriptionPlain": "Build services plain",
                            "opening": "<p>Opening</p>",
                            "openingPlain": "Opening plain",
                            "descriptionBody": "<p>Body</p>",
                            "descriptionBodyPlain": "Body plain",
                            "lists": [{"text": "What you do", "content": "<ul><li>Build</li></ul>"}],
                            "additional": "<p>Additional</p>",
                            "additionalPlain": "Additional plain",
                            "hostedUrl": "https://jobs.lever.co/fake/posting-1",
                            "applyUrl": "https://jobs.lever.co/fake/posting-1/apply",
                            "workplaceType": "remote",
                            "salaryRange": {
                              "currency": "USD",
                              "interval": "year",
                              "min": 50000,
                              "max": 70000
                            },
                            "salaryDescription": "<p>Salary</p>",
                            "salaryDescriptionPlain": "Salary plain",
                            "ignored": "value"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<LeverJobClient.LeverPosting> postings = fixture.client().fetchJobs("approved_site", LeverRegion.GLOBAL);

        assertThat(postings).singleElement().satisfies(posting -> {
            assertThat(posting.getId()).isEqualTo("posting-1");
            assertThat(posting.getText()).isEqualTo("Backend Engineer");
            assertThat(posting.getCategories().getLocation()).isEqualTo("Remote");
            assertThat(posting.getCategories().getCommitment()).isEqualTo("Full-time");
            assertThat(posting.getCategories().getTeam()).isEqualTo("Engineering");
            assertThat(posting.getCategories().getDepartment()).isEqualTo("Platform");
            assertThat(posting.getCategories().getAllLocations()).containsExactly("Remote", "London");
            assertThat(posting.getCountry()).isEqualTo("US");
            assertThat(posting.getDescription()).isEqualTo("<p>Build services</p>");
            assertThat(posting.getDescriptionPlain()).isEqualTo("Build services plain");
            assertThat(posting.getOpening()).isEqualTo("<p>Opening</p>");
            assertThat(posting.getOpeningPlain()).isEqualTo("Opening plain");
            assertThat(posting.getDescriptionBody()).isEqualTo("<p>Body</p>");
            assertThat(posting.getDescriptionBodyPlain()).isEqualTo("Body plain");
            assertThat(posting.getLists()).singleElement().satisfies(section -> {
                assertThat(section.getText()).isEqualTo("What you do");
                assertThat(section.getContent()).isEqualTo("<ul><li>Build</li></ul>");
            });
            assertThat(posting.getAdditional()).isEqualTo("<p>Additional</p>");
            assertThat(posting.getAdditionalPlain()).isEqualTo("Additional plain");
            assertThat(posting.getHostedUrl()).isEqualTo("https://jobs.lever.co/fake/posting-1");
            assertThat(posting.getApplyUrl()).isEqualTo("https://jobs.lever.co/fake/posting-1/apply");
            assertThat(posting.getWorkplaceType()).isEqualTo("remote");
            assertThat(posting.getSalaryRange().getCurrency()).isEqualTo("USD");
            assertThat(posting.getSalaryRange().getInterval()).isEqualTo("year");
            assertThat(posting.getSalaryRange().getMin()).isEqualByComparingTo("50000");
            assertThat(posting.getSalaryRange().getMax()).isEqualByComparingTo("70000");
            assertThat(posting.getSalaryDescription()).isEqualTo("<p>Salary</p>");
            assertThat(posting.getSalaryDescriptionPlain()).isEqualTo("Salary plain");
        });
        fixture.server().verify();
    }

    @Test
    void fetchJobsUsesEuBaseUrlAndEncodesSiteAsPathSegment() {
        ClientFixture fixture = clientFixture(100, 20);
        fixture.server().expect(requestTo(EU_API_URL + "/approved%20site?mode=json&skip=0&limit=100"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(fixture.client().fetchJobs("approved site", LeverRegion.EU)).isEmpty();
        fixture.server().verify();
    }

    @Test
    void emptyFirstPageIsSuccessfulZeroCurrentJobs() {
        ClientFixture fixture = clientFixture(2, 3);
        fixture.server().expect(requestTo(GLOBAL_API_URL + "/empty_site?mode=json&skip=0&limit=2"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(fixture.client().fetchJobs("empty_site", LeverRegion.GLOBAL)).isEmpty();
        fixture.server().verify();
    }

    @Test
    void fetchJobsAccumulatesMultiplePagesUntilFinalPartialPage() {
        ClientFixture fixture = clientFixture(2, 3);
        fixture.server().expect(requestTo(GLOBAL_API_URL + "/paged_site?mode=json&skip=0&limit=2"))
                .andRespond(withSuccess("""
                        [
                          {"id":"posting-1","text":"One"},
                          {"id":"posting-2","text":"Two"}
                        ]
                        """, MediaType.APPLICATION_JSON));
        fixture.server().expect(requestTo(GLOBAL_API_URL + "/paged_site?mode=json&skip=2&limit=2"))
                .andRespond(withSuccess("""
                        [
                          {"id":"posting-3","text":"Three"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<LeverJobClient.LeverPosting> postings = fixture.client().fetchJobs("paged_site", LeverRegion.GLOBAL);

        assertThat(postings)
                .extracting(LeverJobClient.LeverPosting::getId)
                .containsExactly("posting-1", "posting-2", "posting-3");
        fixture.server().verify();
    }

    @Test
    void repeatedCompletePagesFailSafely() {
        ClientFixture fixture = clientFixture(2, 3);
        fixture.server().expect(requestTo(GLOBAL_API_URL + "/loop_site?mode=json&skip=0&limit=2"))
                .andRespond(withSuccess("""
                        [
                          {"id":"posting-1","text":"One"},
                          {"id":"posting-2","text":"Two"}
                        ]
                        """, MediaType.APPLICATION_JSON));
        fixture.server().expect(requestTo(GLOBAL_API_URL + "/loop_site?mode=json&skip=2&limit=2"))
                .andRespond(withSuccess("""
                        [
                          {"id":"posting-1","text":"One"},
                          {"id":"posting-2","text":"Two"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().fetchJobs("loop_site", LeverRegion.GLOBAL))
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Failed to fetch jobs from Lever API");
        fixture.server().verify();
    }

    @Test
    void reachingMaxPagesOnAFullPageFailsSafely() {
        ClientFixture fixture = clientFixture(1, 1);
        fixture.server().expect(requestTo(GLOBAL_API_URL + "/large_site?mode=json&skip=0&limit=1"))
                .andRespond(withSuccess("[{\"id\":\"posting-1\",\"text\":\"One\"}]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().fetchJobs("large_site", LeverRegion.GLOBAL))
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Failed to fetch jobs from Lever API");
        fixture.server().verify();
    }

    @Test
    void fetchJobsWrapsHttp4xxAnd5xxWithoutRawResponseBody() {
        ClientFixture badRequest = clientFixture(2, 3);
        badRequest.server().expect(requestTo(GLOBAL_API_URL + "/bad_site?mode=json&skip=0&limit=2"))
                .andRespond(withBadRequest()
                        .body("Authorization: Bearer secret-token")
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> badRequest.client().fetchJobs("bad_site", LeverRegion.GLOBAL))
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Failed to fetch jobs from Lever API")
                .hasMessageNotContaining("secret-token");
        badRequest.server().verify();

        ClientFixture serverError = clientFixture(2, 3);
        serverError.server().expect(requestTo(GLOBAL_API_URL + "/server_site?mode=json&skip=0&limit=2"))
                .andRespond(withServerError()
                        .body("provider response body with private data")
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> serverError.client().fetchJobs("server_site", LeverRegion.GLOBAL))
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Failed to fetch jobs from Lever API")
                .hasMessageNotContaining("private data");
        serverError.server().verify();
    }

    @Test
    void fetchJobsWrapsMalformedJson() {
        ClientFixture fixture = clientFixture(2, 3);
        fixture.server().expect(requestTo(GLOBAL_API_URL + "/malformed_site?mode=json&skip=0&limit=2"))
                .andRespond(withSuccess("[", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().fetchJobs("malformed_site", LeverRegion.GLOBAL))
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Failed to fetch jobs from Lever API");
        fixture.server().verify();
    }

    @Test
    void requestFactoryUsesConfiguredTimeoutsWithoutSlowRuntimeTest() {
        OpportunityProviderProperties.Lever lever = new OpportunityProviderProperties.Lever();
        lever.setConnectionTimeout(Duration.ofSeconds(2));
        lever.setReadTimeout(Duration.ofSeconds(3));

        SimpleClientHttpRequestFactory factory = LeverJobClient.createRequestFactory(lever);

        assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(2000);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(3000);
    }

    @Test
    void paginationConfigurationIsConstrained() {
        OpportunityProviderProperties.Lever lever = new OpportunityProviderProperties.Lever();

        lever.setPageSize(0);
        assertThat(lever.getPageSize()).isEqualTo(100);
        lever.setPageSize(500);
        assertThat(lever.getPageSize()).isEqualTo(100);

        lever.setMaxPages(0);
        assertThat(lever.getMaxPages()).isEqualTo(20);
        lever.setMaxPages(500);
        assertThat(lever.getMaxPages()).isEqualTo(20);
    }

    private ClientFixture clientFixture(int pageSize, int maxPages) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LeverJobClient client = new LeverJobClient(builder, properties(pageSize, maxPages), false);
        return new ClientFixture(client, server);
    }

    private OpportunityProviderProperties properties(int pageSize, int maxPages) {
        OpportunityProviderProperties properties = new OpportunityProviderProperties();
        OpportunityProviderProperties.Lever lever = properties.getProviders().getLever();
        lever.setGlobalApiUrl(GLOBAL_API_URL);
        lever.setEuApiUrl(EU_API_URL);
        lever.setPageSize(pageSize);
        lever.setMaxPages(maxPages);
        return properties;
    }

    private record ClientFixture(LeverJobClient client, MockRestServiceServer server) {
    }
}

package com.arclume.api.client;

import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.service.opportunity.OpportunityProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GreenhouseJobClientTest {

    private static final String API_URL = "https://greenhouse.test/v1/boards";

    @Test
    void fetchJobsUsesConfiguredBaseUrlPathSegmentAndContentQuery() {
        ClientFixture fixture = clientFixture();
        fixture.server().expect(requestTo(API_URL + "/approved_token-1/jobs?content=true"))
                .andRespond(withSuccess("""
                        {
                          "jobs": [
                            {
                              "id": 12345,
                              "internal_job_id": 67890,
                              "title": "Platform Engineer",
                              "updated_at": "2026-07-18T10:15:30Z",
                              "location": {"name": "Remote"},
                              "absolute_url": "https://boards.greenhouse.io/fake/jobs/12345",
                              "language": "en",
                              "content": "<p>Build APIs</p>",
                              "departments": [{"id": 1, "name": "Engineering"}],
                              "offices": [{"id": 2, "name": "Remote Office"}],
                              "unknown_field": "ignored"
                            }
                          ],
                          "meta": {"total": 1}
                        }
                        """, MediaType.APPLICATION_JSON));

        List<GreenhouseJobClient.GreenhouseJob> jobs = fixture.client().fetchJobs("approved_token-1");

        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.getId()).isEqualTo("12345");
            assertThat(job.getInternalJobId()).isEqualTo("67890");
            assertThat(job.getTitle()).isEqualTo("Platform Engineer");
            assertThat(job.getUpdatedAt()).isEqualTo("2026-07-18T10:15:30Z");
            assertThat(job.getLocation().getName()).isEqualTo("Remote");
            assertThat(job.getAbsoluteUrl()).isEqualTo("https://boards.greenhouse.io/fake/jobs/12345");
            assertThat(job.getLanguage()).isEqualTo("en");
            assertThat(job.getContent()).isEqualTo("<p>Build APIs</p>");
            assertThat(job.getDepartments()).singleElement().satisfies(department -> {
                assertThat(department.getId()).isEqualTo("1");
                assertThat(department.getName()).isEqualTo("Engineering");
            });
            assertThat(job.getOffices()).singleElement().satisfies(office -> {
                assertThat(office.getId()).isEqualTo("2");
                assertThat(office.getName()).isEqualTo("Remote Office");
            });
        });
        fixture.server().verify();
    }

    @Test
    void boardTokenIsEncodedAsAPathSegment() {
        ClientFixture fixture = clientFixture();
        fixture.server().expect(requestTo(API_URL + "/approved%20token/jobs?content=true"))
                .andRespond(withSuccess("{\"jobs\":[]}", MediaType.APPLICATION_JSON));

        assertThat(fixture.client().fetchJobs("approved token")).isEmpty();
        fixture.server().verify();
    }

    @Test
    void missingNullAndEmptyJobsReturnEmptyLists() {
        ClientFixture missingJobs = clientFixture();
        missingJobs.server().expect(requestTo(API_URL + "/missing/jobs?content=true"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThat(missingJobs.client().fetchJobs("missing")).isEmpty();
        missingJobs.server().verify();

        ClientFixture nullJobs = clientFixture();
        nullJobs.server().expect(requestTo(API_URL + "/null/jobs?content=true"))
                .andRespond(withSuccess("{\"jobs\":null}", MediaType.APPLICATION_JSON));
        assertThat(nullJobs.client().fetchJobs("null")).isEmpty();
        nullJobs.server().verify();

        ClientFixture emptyJobs = clientFixture();
        emptyJobs.server().expect(requestTo(API_URL + "/empty/jobs?content=true"))
                .andRespond(withSuccess("{\"jobs\":[]}", MediaType.APPLICATION_JSON));
        assertThat(emptyJobs.client().fetchJobs("empty")).isEmpty();
        emptyJobs.server().verify();
    }

    @Test
    void fetchJobsWrapsHttp4xxWithoutRawResponseBody() {
        ClientFixture fixture = clientFixture();
        fixture.server().expect(requestTo(API_URL + "/bad/jobs?content=true"))
                .andRespond(withBadRequest()
                        .body("Authorization: Bearer secret-token")
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> fixture.client().fetchJobs("bad"))
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Failed to fetch jobs from Greenhouse API")
                .hasMessageNotContaining("secret-token");
        fixture.server().verify();
    }

    @Test
    void fetchJobsWrapsHttp5xxWithoutRawResponseBody() {
        ClientFixture fixture = clientFixture();
        fixture.server().expect(requestTo(API_URL + "/server/jobs?content=true"))
                .andRespond(withServerError()
                        .body("provider response body with private data")
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> fixture.client().fetchJobs("server"))
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Failed to fetch jobs from Greenhouse API")
                .hasMessageNotContaining("private data");
        fixture.server().verify();
    }

    @Test
    void fetchJobsWrapsMalformedJsonAndIncompatibleResponses() {
        ClientFixture malformed = clientFixture();
        malformed.server().expect(requestTo(API_URL + "/malformed/jobs?content=true"))
                .andRespond(withSuccess("{\"jobs\":[", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> malformed.client().fetchJobs("malformed"))
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Failed to fetch jobs from Greenhouse API");
        malformed.server().verify();

        ClientFixture incompatible = clientFixture();
        incompatible.server().expect(requestTo(API_URL + "/incompatible/jobs?content=true"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> incompatible.client().fetchJobs("incompatible"))
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Failed to fetch jobs from Greenhouse API");
        incompatible.server().verify();
    }

    @Test
    void requestFactoryUsesConfiguredTimeoutsWithoutSlowRuntimeTest() {
        OpportunityProviderProperties.Greenhouse greenhouse = new OpportunityProviderProperties.Greenhouse();
        greenhouse.setConnectionTimeout(Duration.ofSeconds(2));
        greenhouse.setReadTimeout(Duration.ofSeconds(3));

        SimpleClientHttpRequestFactory factory = GreenhouseJobClient.createRequestFactory(greenhouse);

        assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(2000);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(3000);
    }

    private ClientFixture clientFixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GreenhouseJobClient client = new GreenhouseJobClient(builder, properties(), false);
        return new ClientFixture(client, server);
    }

    private OpportunityProviderProperties properties() {
        OpportunityProviderProperties properties = new OpportunityProviderProperties();
        properties.getProviders().getGreenhouse().setApiUrl(API_URL);
        return properties;
    }

    private record ClientFixture(GreenhouseJobClient client, MockRestServiceServer server) {
    }
}

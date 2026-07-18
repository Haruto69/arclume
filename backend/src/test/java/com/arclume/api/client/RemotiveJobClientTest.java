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

class RemotiveJobClientTest {

    private static final String API_URL = "https://remotive.test/api/remote-jobs";

    @Test
    void fetchJobsDeserializesValidPayloadAndUsesConfiguredBaseUrl() {
        ClientFixture fixture = clientFixture();
        fixture.server().expect(requestTo(API_URL))
                .andRespond(withSuccess("""
                        {
                          "jobs": [
                            {
                              "id": "remotive-1",
                              "url": "https://remotive.com/remote-jobs/software-dev/java-developer",
                              "title": "Java Developer",
                              "company_name": "Arclume Labs",
                              "job_type": "full_time",
                              "publication_date": "2026-07-18T10:15:30Z",
                              "candidate_required_location": "Worldwide",
                              "salary": "$120k",
                              "description": "<p>Build APIs</p>"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RemotiveJobClient.RemotiveJob> jobs = fixture.client().fetchJobs();

        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.getId()).isEqualTo("remotive-1");
            assertThat(job.getUrl()).isEqualTo("https://remotive.com/remote-jobs/software-dev/java-developer");
            assertThat(job.getTitle()).isEqualTo("Java Developer");
            assertThat(job.getCompanyName()).isEqualTo("Arclume Labs");
            assertThat(job.getJobType()).isEqualTo("full_time");
            assertThat(job.getPublicationDate()).isEqualTo("2026-07-18T10:15:30Z");
            assertThat(job.getCandidateRequiredLocation()).isEqualTo("Worldwide");
            assertThat(job.getSalary()).isEqualTo("$120k");
            assertThat(job.getDescription()).isEqualTo("<p>Build APIs</p>");
        });
        fixture.server().verify();
    }

    @Test
    void fetchJobsReturnsEmptyListForEmptyJobsArray() {
        ClientFixture fixture = clientFixture();
        fixture.server().expect(requestTo(API_URL))
                .andRespond(withSuccess("{\"jobs\":[]}", MediaType.APPLICATION_JSON));

        assertThat(fixture.client().fetchJobs()).isEmpty();
        fixture.server().verify();
    }

    @Test
    void fetchJobsReturnsEmptyListForMissingOrNullJobsField() {
        ClientFixture missingJobs = clientFixture();
        missingJobs.server().expect(requestTo(API_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(missingJobs.client().fetchJobs()).isEmpty();
        missingJobs.server().verify();

        ClientFixture nullJobs = clientFixture();
        nullJobs.server().expect(requestTo(API_URL))
                .andRespond(withSuccess("{\"jobs\":null}", MediaType.APPLICATION_JSON));

        assertThat(nullJobs.client().fetchJobs()).isEmpty();
        nullJobs.server().verify();
    }

    @Test
    void fetchJobsWrapsHttp4xxResponse() {
        ClientFixture fixture = clientFixture();
        fixture.server().expect(requestTo(API_URL))
                .andRespond(withBadRequest()
                        .body("Authorization: Bearer secret-token")
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(fixture.client()::fetchJobs)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Failed to fetch jobs from Remotive API");
        fixture.server().verify();
    }

    @Test
    void fetchJobsWrapsHttp5xxResponse() {
        ClientFixture fixture = clientFixture();
        fixture.server().expect(requestTo(API_URL))
                .andRespond(withServerError()
                        .body("token=secret-token")
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(fixture.client()::fetchJobs)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Failed to fetch jobs from Remotive API");
        fixture.server().verify();
    }

    @Test
    void fetchJobsWrapsMalformedJson() {
        ClientFixture fixture = clientFixture();
        fixture.server().expect(requestTo(API_URL))
                .andRespond(withSuccess("{\"jobs\":[", MediaType.APPLICATION_JSON));

        assertThatThrownBy(fixture.client()::fetchJobs)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Failed to fetch jobs from Remotive API");
        fixture.server().verify();
    }

    @Test
    void requestFactoryUsesConfiguredTimeoutsWithoutSlowRuntimeTest() {
        OpportunityProviderProperties.Remotive remotive = new OpportunityProviderProperties.Remotive();
        remotive.setConnectionTimeout(Duration.ofSeconds(2));
        remotive.setReadTimeout(Duration.ofSeconds(3));

        SimpleClientHttpRequestFactory factory = RemotiveJobClient.createRequestFactory(remotive);

        assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(2000);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(3000);
    }

    private ClientFixture clientFixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RemotiveJobClient client = new RemotiveJobClient(builder, properties(), false);
        return new ClientFixture(client, server);
    }

    private OpportunityProviderProperties properties() {
        OpportunityProviderProperties properties = new OpportunityProviderProperties();
        properties.getProviders().getRemotive().setApiUrl(API_URL);
        return properties;
    }

    private record ClientFixture(RemotiveJobClient client, MockRestServiceServer server) {
    }
}

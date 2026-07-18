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

class JobicyJobClientTest {

    private static final String API_URL = "https://jobicy.test/api/v2/remote-jobs";

    @Test
    void fetchJobsDeserializesCurrentPayloadAndUsesConfiguredBaseUrlWithCount() {
        ClientFixture fixture = clientFixture(50);
        fixture.server().expect(requestTo(API_URL + "?count=50"))
                .andRespond(withSuccess("""
                        {
                          "jobs": [
                            {
                              "id": 12345,
                              "url": "https://jobicy.com/jobs/12345-java-developer",
                              "jobTitle": "Java Developer",
                              "companyName": "Arclume Labs",
                              "companyLogo": "https://jobicy.com/logo.png",
                              "jobIndustry": "Software",
                              "jobType": "full-time",
                              "jobGeo": "Worldwide",
                              "jobLevel": "Senior",
                              "jobExcerpt": "Build APIs",
                              "jobDescription": "<p>Build APIs</p>",
                              "pubDate": "2026-07-18T10:15:30Z",
                              "salaryMin": 50000,
                              "salaryMax": 70000,
                              "salaryCurrency": "USD",
                              "salaryPeriod": "year"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<JobicyJobClient.JobicyJob> jobs = fixture.client().fetchJobs();

        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.getId()).isEqualTo("12345");
            assertThat(job.getUrl()).isEqualTo("https://jobicy.com/jobs/12345-java-developer");
            assertThat(job.getJobTitle()).isEqualTo("Java Developer");
            assertThat(job.getCompanyName()).isEqualTo("Arclume Labs");
            assertThat(job.getCompanyLogo()).isEqualTo("https://jobicy.com/logo.png");
            assertThat(job.getJobIndustry()).isEqualTo("Software");
            assertThat(job.getJobType()).isEqualTo("full-time");
            assertThat(job.getJobGeo()).isEqualTo("Worldwide");
            assertThat(job.getJobLevel()).isEqualTo("Senior");
            assertThat(job.getJobExcerpt()).isEqualTo("Build APIs");
            assertThat(job.getJobDescription()).isEqualTo("<p>Build APIs</p>");
            assertThat(job.getPubDate()).isEqualTo("2026-07-18T10:15:30Z");
            assertThat(job.getSalaryMin()).isEqualByComparingTo("50000");
            assertThat(job.getSalaryMax()).isEqualByComparingTo("70000");
            assertThat(job.getSalaryCurrency()).isEqualTo("USD");
            assertThat(job.getSalaryPeriod()).isEqualTo("year");
        });
        fixture.server().verify();
    }

    @Test
    void fetchJobsDeserializesLegacyAnnualSalaryFieldsAndTextualIds() {
        ClientFixture fixture = clientFixture(25);
        fixture.server().expect(requestTo(API_URL + "?count=25"))
                .andRespond(withSuccess("""
                        {
                          "jobs": [
                            {
                              "id": "jobicy-text-id",
                              "url": "https://jobicy.com/jobs/text-id",
                              "jobTitle": "Frontend Developer",
                              "companyName": "Web Co",
                              "annualSalaryMin": 40000,
                              "annualSalaryMax": 60000
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<JobicyJobClient.JobicyJob> jobs = fixture.client().fetchJobs();

        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.getId()).isEqualTo("jobicy-text-id");
            assertThat(job.getSalaryMin()).isEqualByComparingTo("40000");
            assertThat(job.getSalaryMax()).isEqualByComparingTo("60000");
        });
        fixture.server().verify();
    }

    @Test
    void fetchJobsReturnsEmptyListForEmptyJobsArray() {
        ClientFixture fixture = clientFixture(50);
        fixture.server().expect(requestTo(API_URL + "?count=50"))
                .andRespond(withSuccess("{\"jobs\":[]}", MediaType.APPLICATION_JSON));

        assertThat(fixture.client().fetchJobs()).isEmpty();
        fixture.server().verify();
    }

    @Test
    void fetchJobsReturnsEmptyListForMissingOrNullJobsField() {
        ClientFixture missingJobs = clientFixture(50);
        missingJobs.server().expect(requestTo(API_URL + "?count=50"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(missingJobs.client().fetchJobs()).isEmpty();
        missingJobs.server().verify();

        ClientFixture nullJobs = clientFixture(50);
        nullJobs.server().expect(requestTo(API_URL + "?count=50"))
                .andRespond(withSuccess("{\"jobs\":null}", MediaType.APPLICATION_JSON));

        assertThat(nullJobs.client().fetchJobs()).isEmpty();
        nullJobs.server().verify();
    }

    @Test
    void fetchJobsReturnsEmptyListForNullResponseBodyWhenSupportedByInfrastructure() {
        ClientFixture fixture = clientFixture(50);
        fixture.server().expect(requestTo(API_URL + "?count=50"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        assertThat(fixture.client().fetchJobs()).isEmpty();
        fixture.server().verify();
    }

    @Test
    void fetchJobsWrapsHttp4xxResponseWithoutRawResponseBody() {
        ClientFixture fixture = clientFixture(50);
        fixture.server().expect(requestTo(API_URL + "?count=50"))
                .andRespond(withBadRequest()
                        .body("Authorization: Bearer secret-token")
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(fixture.client()::fetchJobs)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Failed to fetch jobs from Jobicy API")
                .hasMessageNotContaining("secret-token");
        fixture.server().verify();
    }

    @Test
    void fetchJobsWrapsHttp5xxResponseWithoutRawResponseBody() {
        ClientFixture fixture = clientFixture(50);
        fixture.server().expect(requestTo(API_URL + "?count=50"))
                .andRespond(withServerError()
                        .body("token=secret-token")
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(fixture.client()::fetchJobs)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Failed to fetch jobs from Jobicy API")
                .hasMessageNotContaining("secret-token");
        fixture.server().verify();
    }

    @Test
    void fetchJobsWrapsMalformedJson() {
        ClientFixture fixture = clientFixture(50);
        fixture.server().expect(requestTo(API_URL + "?count=50"))
                .andRespond(withSuccess("{\"jobs\":[", MediaType.APPLICATION_JSON));

        assertThatThrownBy(fixture.client()::fetchJobs)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Failed to fetch jobs from Jobicy API");
        fixture.server().verify();
    }

    @Test
    void requestFactoryUsesConfiguredTimeoutsWithoutSlowRuntimeTest() {
        OpportunityProviderProperties.Jobicy jobicy = new OpportunityProviderProperties.Jobicy();
        jobicy.setConnectionTimeout(Duration.ofSeconds(2));
        jobicy.setReadTimeout(Duration.ofSeconds(3));

        SimpleClientHttpRequestFactory factory = JobicyJobClient.createRequestFactory(jobicy);

        assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(2000);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(3000);
    }

    @Test
    void invalidCountConfigurationIsSafelyConstrained() {
        OpportunityProviderProperties.Jobicy jobicy = new OpportunityProviderProperties.Jobicy();

        jobicy.setCount(0);
        assertThat(jobicy.getCount()).isEqualTo(50);

        jobicy.setCount(-5);
        assertThat(jobicy.getCount()).isEqualTo(50);

        jobicy.setCount(500);
        assertThat(jobicy.getCount()).isEqualTo(100);
    }

    private ClientFixture clientFixture(int count) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        JobicyJobClient client = new JobicyJobClient(builder, properties(count), false);
        return new ClientFixture(client, server);
    }

    private OpportunityProviderProperties properties(int count) {
        OpportunityProviderProperties properties = new OpportunityProviderProperties();
        properties.getProviders().getJobicy().setApiUrl(API_URL);
        properties.getProviders().getJobicy().setCount(count);
        return properties;
    }

    private record ClientFixture(JobicyJobClient client, MockRestServiceServer server) {
    }
}

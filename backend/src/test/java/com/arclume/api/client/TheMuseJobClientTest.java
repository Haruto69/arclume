package com.arclume.api.client;

import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.service.opportunity.OpportunityProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TheMuseJobClientTest {

    private static final String API_URL = "https://muse.test/api/public";
    private static final String API_KEY = "test-muse-key";
    private static final String PROVIDER_BODY_TEXT = "provider response body with private data";
    private static final String RATE_LIMIT_TEXT = "rate limit exceeded by upstream";
    private static final String HTML_CHALLENGE_TEXT = "html challenge content";
    private static final String AUTH_SECRET_TEXT = "Authorization: Bearer secret-token";
    private static final String JOBS_PAGE_ZERO_URL = jobsPageUrl(0);

    @Test
    void fetchPageUsesConfiguredBaseUrlPathQueryApiKeyAndDeserializesJobFields() {
        ClientFixture fixture = clientFixture();
        fixture.server().expect(requestTo(JOBS_PAGE_ZERO_URL))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andExpect(headerDoesNotExist(HttpHeaders.COOKIE))
                .andRespond(withSuccess("""
                        {
                          "page": 0,
                          "page_count": 3,
                          "results": [
                            {
                              "id": 12345,
                              "name": "Backend Engineer",
                              "short_name": "backend-engineer",
                              "contents": "<p>Build services</p>",
                              "publication_date": "2026-07-18T10:15:30Z",
                              "type": "external",
                              "company": {
                                "id": 567,
                                "name": "Arclume Labs",
                                "short_name": "arclume-labs",
                                "ignored": "value"
                              },
                              "locations": [{"name": "Remote"}],
                              "levels": [{"name": "Internship"}],
                              "categories": [{"name": "Engineering"}],
                              "refs": {"landing_page": "https://www.themuse.com/jobs/arclume/backend-engineer"},
                              "ignored": "value"
                            }
                          ],
                          "ignored": "value"
                        }
                        """, MediaType.APPLICATION_JSON));

        TheMuseJobClient.TheMuseJobPage page = fixture.client().fetchPage(0, API_KEY);

        assertThat(page.page()).isZero();
        assertThat(page.pageCount()).isEqualTo(3);
        assertThat(page.results()).singleElement().satisfies(job -> {
            assertThat(job.getId()).isEqualTo(12345L);
            assertThat(job.getName()).isEqualTo("Backend Engineer");
            assertThat(job.getShortName()).isEqualTo("backend-engineer");
            assertThat(job.getContents()).isEqualTo("<p>Build services</p>");
            assertThat(job.getPublicationDate()).isEqualTo("2026-07-18T10:15:30Z");
            assertThat(job.getType()).isEqualTo("external");
            assertThat(job.getCompany().getId()).isEqualTo(567L);
            assertThat(job.getCompany().getName()).isEqualTo("Arclume Labs");
            assertThat(job.getCompany().getShortName()).isEqualTo("arclume-labs");
            assertThat(job.getLocations()).singleElement().satisfies(location -> assertThat(location.getName()).isEqualTo("Remote"));
            assertThat(job.getLevels()).singleElement().satisfies(level -> assertThat(level.getName()).isEqualTo("Internship"));
            assertThat(job.getCategories()).singleElement().satisfies(category -> assertThat(category.getName()).isEqualTo("Engineering"));
            assertThat(job.getRefs().getLandingPage()).isEqualTo("https://www.themuse.com/jobs/arclume/backend-engineer");
        });
        fixture.server().verify();
    }

    @Test
    void emptyResultsAndZeroPageCountAreSuccessfulAndDefensive() {
        ClientFixture emptyPage = clientFixture();
        emptyPage.server().expect(requestTo(JOBS_PAGE_ZERO_URL))
                .andRespond(withSuccess("{\"page\":0,\"page_count\":1,\"results\":[]}", MediaType.APPLICATION_JSON));

        assertThat(emptyPage.client().fetchPage(0, API_KEY).results()).isEmpty();
        emptyPage.server().verify();

        ClientFixture zeroPageCount = clientFixture();
        zeroPageCount.server().expect(requestTo(JOBS_PAGE_ZERO_URL))
                .andRespond(withSuccess("{\"page\":0,\"page_count\":0,\"results\":[]}", MediaType.APPLICATION_JSON));

        TheMuseJobClient.TheMuseJobPage page = zeroPageCount.client().fetchPage(0, API_KEY);

        assertThat(page.pageCount()).isZero();
        assertThat(page.results()).isEmpty();
        assertThatThrownBy(() -> page.results().add(new TheMuseJobClient.TheMuseJob()))
                .isInstanceOf(UnsupportedOperationException.class);
        zeroPageCount.server().verify();
    }

    @Test
    void incompatibleEnvelopesFailWithFixedSafeMessage() {
        assertEnvelopeFailureForBody("null");
        assertEnvelopeFailureForBody("{\"page_count\":1,\"results\":[]}");
        assertEnvelopeFailureForBody("{\"page\":-1,\"page_count\":1,\"results\":[]}");
        assertEnvelopeFailureForBody("{\"page\":1,\"page_count\":2,\"results\":[]}");
        assertEnvelopeFailureForBody("{\"page\":0,\"results\":[]}");
        assertEnvelopeFailureForBody("{\"page\":0,\"page_count\":-1,\"results\":[]}");
        assertEnvelopeFailureForBody("{\"page\":0,\"page_count\":1}");
        assertEnvelopeFailureForBody("{\"page\":0,\"page_count\":0,\"results\":[{\"id\":1}]}");
        assertEnvelopeFailureForBody(1, "{\"page\":1,\"page_count\":1,\"results\":[]}");
        assertEnvelopeFailureForBody(2, "{\"page\":2,\"page_count\":2,\"results\":[]}");
    }

    @Test
    void documentedHttpFailuresUseFixedSafeThrowableChainWithoutBodyOrKeyLeakage() {
        assertRequestFailureForResponse(withBadRequest()
                .body("bad request body api_key=" + API_KEY + " " + AUTH_SECRET_TEXT)
                .contentType(MediaType.TEXT_PLAIN));
        assertRequestFailureForResponse(withStatus(HttpStatus.FORBIDDEN)
                .header("X-RateLimit-Remaining", "0")
                .body(RATE_LIMIT_TEXT + " api_key=" + API_KEY + " " + AUTH_SECRET_TEXT)
                .contentType(MediaType.TEXT_PLAIN));
        assertRequestFailureForResponse(withStatus(HttpStatus.NOT_FOUND)
                .body("not found api_key=" + API_KEY + " " + AUTH_SECRET_TEXT)
                .contentType(MediaType.TEXT_PLAIN));
        assertRequestFailureForResponse(withServerError()
                .body(PROVIDER_BODY_TEXT + " api_key=" + API_KEY + " " + AUTH_SECRET_TEXT)
                .contentType(MediaType.TEXT_PLAIN));
    }

    @Test
    void malformedJsonHtmlAndTimeoutsUseFixedSafeThrowableChain() {
        assertRequestFailureForResponse(withSuccess("{\"page\":0,\"page_count\":1,\"results\":[", MediaType.APPLICATION_JSON));
        assertRequestFailureForResponse(withSuccess(
                "<html>" + HTML_CHALLENGE_TEXT + " api_key=" + API_KEY + " " + AUTH_SECRET_TEXT + "</html>",
                MediaType.TEXT_HTML));
        assertRequestFailureForResponse(withException(new SocketTimeoutException(
                "socket timed out api_key=" + API_KEY + " " + AUTH_SECRET_TEXT)));
    }

    @Test
    void requestFactoryUsesConfiguredTimeoutsWithoutSlowRuntimeTest() {
        OpportunityProviderProperties.TheMuse theMuse = new OpportunityProviderProperties.TheMuse();
        theMuse.setConnectionTimeout(Duration.ofSeconds(2));
        theMuse.setReadTimeout(Duration.ofSeconds(3));

        SimpleClientHttpRequestFactory factory = TheMuseJobClient.createRequestFactory(theMuse);

        assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(2000);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(3000);
    }

    @Test
    void configurationDefaultsAndMaxPagesAreSafelyConstrained() {
        OpportunityProviderProperties.TheMuse theMuse = new OpportunityProviderProperties.TheMuse();

        assertThat(theMuse.isEnabled()).isFalse();
        assertThat(theMuse.getApiUrl()).isEqualTo("https://www.themuse.com/api/public");
        assertThat(theMuse.getApiKey()).isEmpty();
        assertThat(theMuse.getMaxPages()).isEqualTo(10);
        assertThat(theMuse.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(theMuse.getReadTimeout()).isEqualTo(Duration.ofSeconds(5));

        theMuse.setMaxPages(0);
        assertThat(theMuse.getMaxPages()).isEqualTo(10);
        theMuse.setMaxPages(-5);
        assertThat(theMuse.getMaxPages()).isEqualTo(10);
        theMuse.setMaxPages(500);
        assertThat(theMuse.getMaxPages()).isEqualTo(50);
    }

    private void assertEnvelopeFailureForBody(String body) {
        assertEnvelopeFailureForBody(0, body);
    }

    private void assertEnvelopeFailureForBody(int page, String body) {
        OpportunityProviderException exception = captureFailureForResponse(
                page,
                withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(exception).hasMessage(TheMuseJobClient.FAILURE_MESSAGE);
        assertThat(exception.getCause()).isNull();
        assertThrowableChainHasNoSensitiveText(exception, jobsPageUrl(page));
    }

    private void assertRequestFailureForResponse(ResponseCreator responseCreator) {
        assertRequestFailureForResponse(0, responseCreator);
    }

    private void assertRequestFailureForResponse(int page, ResponseCreator responseCreator) {
        OpportunityProviderException exception = captureFailureForResponse(page, responseCreator);

        assertThat(exception).hasMessage(TheMuseJobClient.FAILURE_MESSAGE);
        assertThat(exception.getCause())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The Muse API request failed");
        assertThat(exception.getCause().getCause()).isNull();
        assertThrowableChainHasNoSensitiveText(exception, jobsPageUrl(page));
        assertThrowableChainDoesNotRetainSpringExceptions(exception);
    }

    private OpportunityProviderException captureFailureForResponse(int page, ResponseCreator responseCreator) {
        ClientFixture fixture = clientFixture();
        fixture.server().expect(requestTo(jobsPageUrl(page))).andRespond(responseCreator);

        Throwable thrown = catchThrowable(() -> fixture.client().fetchPage(page, API_KEY));

        fixture.server().verify();
        assertThat(thrown).isInstanceOf(OpportunityProviderException.class);
        return (OpportunityProviderException) thrown;
    }

    private void assertThrowableChainHasNoSensitiveText(Throwable throwable, String requestUrl) {
        List<String> forbidden = List.of(
                API_KEY,
                "api_key",
                API_URL,
                requestUrl,
                PROVIDER_BODY_TEXT,
                RATE_LIMIT_TEXT,
                HTML_CHALLENGE_TEXT,
                AUTH_SECRET_TEXT,
                "bad request body",
                "not found",
                "X-RateLimit-Remaining");
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                forbidden.forEach(value -> assertThat(message).doesNotContain(value));
            }
            current = current.getCause();
        }
    }

    private void assertThrowableChainDoesNotRetainSpringExceptions(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            assertThat(current.getClass().getName()).doesNotStartWith("org.springframework.web.client.");
            current = current.getCause();
        }
    }

    private ClientFixture clientFixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TheMuseJobClient client = new TheMuseJobClient(builder, properties(), false);
        return new ClientFixture(client, server);
    }

    private OpportunityProviderProperties properties() {
        OpportunityProviderProperties properties = new OpportunityProviderProperties();
        properties.getProviders().getTheMuse().setApiUrl(API_URL);
        return properties;
    }

    private static String jobsPageUrl(int page) {
        return API_URL + "/jobs?page=" + page + "&descending=true&api_key=" + API_KEY;
    }

    private record ClientFixture(TheMuseJobClient client, MockRestServiceServer server) {
    }
}

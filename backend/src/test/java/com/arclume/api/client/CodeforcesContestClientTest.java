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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CodeforcesContestClientTest {

    private static final String API_URL = "https://codeforces.test/api";
    private static final String CONTEST_LIST_URL = API_URL + "/contest.list?gym=false&lang=en";

    @Test
    void fetchContestsUsesConfiguredBaseUrlExactPathQueryAndNoAuthHeaders() {
        ClientFixture fixture = clientFixture();
        fixture.server().expect(requestTo(CONTEST_LIST_URL))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(headerDoesNotExist("Cookie"))
                .andRespond(withSuccess("""
                        {
                          "status": "OK",
                          "comment": "ignored",
                          "result": [
                            {
                              "id": 1234,
                              "name": "Codeforces Round",
                              "type": "CF",
                              "phase": "BEFORE",
                              "frozen": false,
                              "durationSeconds": 7200,
                              "startTimeSeconds": 1780000000,
                              "relativeTimeSeconds": -1000,
                              "preparedBy": "tester",
                              "websiteUrl": "https://example.test",
                              "description": "Not persisted",
                              "difficulty": 3,
                              "kind": "Official",
                              "icpcRegion": "EU",
                              "country": "Poland",
                              "city": "Warsaw",
                              "season": "2026-2027",
                              "unknown": "ignored"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<CodeforcesContestClient.CodeforcesContest> contests = fixture.client().fetchContests();

        assertThat(contests).singleElement().satisfies(contest -> {
            assertThat(contest.getId()).isEqualTo(1234L);
            assertThat(contest.getName()).isEqualTo("Codeforces Round");
            assertThat(contest.getType()).isEqualTo("CF");
            assertThat(contest.getPhase()).isEqualTo("BEFORE");
            assertThat(contest.getFrozen()).isFalse();
            assertThat(contest.getDurationSeconds()).isEqualTo(7200L);
            assertThat(contest.getStartTimeSeconds()).isEqualTo(1780000000L);
            assertThat(contest.getRelativeTimeSeconds()).isEqualTo(-1000L);
            assertThat(contest.getPreparedBy()).isEqualTo("tester");
            assertThat(contest.getWebsiteUrl()).isEqualTo("https://example.test");
            assertThat(contest.getDescription()).isEqualTo("Not persisted");
            assertThat(contest.getDifficulty()).isEqualTo(3);
            assertThat(contest.getKind()).isEqualTo("Official");
            assertThat(contest.getIcpcRegion()).isEqualTo("EU");
            assertThat(contest.getCountry()).isEqualTo("Poland");
            assertThat(contest.getCity()).isEqualTo("Warsaw");
            assertThat(contest.getSeason()).isEqualTo("2026-2027");
        });
        fixture.server().verify();
    }

    @Test
    void okEnvelopeWithEmptyResultIsSuccessfulEmptyFetch() {
        ClientFixture fixture = clientFixture();
        fixture.server().expect(requestTo(CONTEST_LIST_URL))
                .andRespond(withSuccess("{\"status\":\"OK\",\"result\":[]}", MediaType.APPLICATION_JSON));

        assertThat(fixture.client().fetchContests()).isEmpty();
        fixture.server().verify();
    }

    @Test
    void okEnvelopeWithNullResultElementReturnsUnmodifiableDefensiveListContainingNull() {
        ClientFixture fixture = clientFixture();
        fixture.server().expect(requestTo(CONTEST_LIST_URL))
                .andRespond(withSuccess("{\"status\":\"OK\",\"result\":[null]}", MediaType.APPLICATION_JSON));

        List<CodeforcesContestClient.CodeforcesContest> contests = fixture.client().fetchContests();

        assertThat(contests).hasSize(1).containsNull();
        assertThatThrownBy(() -> contests.add(new CodeforcesContestClient.CodeforcesContest()))
                .isInstanceOf(UnsupportedOperationException.class);
        fixture.server().verify();
    }
    @Test
    void nullResponseMissingStatusFailedStatusAndOkMissingResultAreProviderFailures() {
        assertFailureForBody("null");
        assertFailureForBody("{}");
        assertFailureForBody("{\"status\":\"FAILED\",\"comment\":\"private provider comment token=secret\",\"result\":[]}");
        assertFailureForBody("{\"status\":\"ok\",\"result\":[]}");
        assertFailureForBody("{\"status\":\"OK\"}");
        assertFailureForBody("{\"status\":\"OK\",\"result\":null}");
    }

    @Test
    void httpFailuresMalformedJsonAndHtmlResponsesUseSafeFixedMessage() {
        assertFailureForResponse(withBadRequest()
                .body("Authorization: Bearer secret-token")
                .contentType(MediaType.TEXT_PLAIN));
        assertFailureForResponse(withServerError()
                .body("provider response body with private data")
                .contentType(MediaType.TEXT_PLAIN));
        assertFailureForBody("{\"status\":\"OK\",\"result\":[");
        assertFailureForResponse(withSuccess("<html>challenge token=secret</html>", MediaType.TEXT_HTML));
    }

    @Test
    void requestFactoryUsesConfiguredTimeoutsWithoutSlowRuntimeTest() {
        OpportunityProviderProperties.Codeforces codeforces = new OpportunityProviderProperties.Codeforces();
        codeforces.setConnectionTimeout(Duration.ofSeconds(2));
        codeforces.setReadTimeout(Duration.ofSeconds(3));

        SimpleClientHttpRequestFactory factory = CodeforcesContestClient.createRequestFactory(codeforces);

        assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(2000);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(3000);
    }

    @Test
    void invalidRetentionConfigurationIsSafelyConstrained() {
        OpportunityProviderProperties.Codeforces codeforces = new OpportunityProviderProperties.Codeforces();

        codeforces.setPastRetentionDays(-1);
        assertThat(codeforces.getPastRetentionDays()).isEqualTo(14);

        codeforces.setPastRetentionDays(0);
        assertThat(codeforces.getPastRetentionDays()).isZero();

        codeforces.setPastRetentionDays(500);
        assertThat(codeforces.getPastRetentionDays()).isEqualTo(365);
    }

    private void assertFailureForBody(String body) {
        assertFailureForResponse(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void assertFailureForResponse(
            org.springframework.test.web.client.ResponseCreator responseCreator) {
        ClientFixture fixture = clientFixture();
        fixture.server().expect(requestTo(CONTEST_LIST_URL)).andRespond(responseCreator);

        assertThatThrownBy(() -> fixture.client().fetchContests())
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage(CodeforcesContestClient.FAILURE_MESSAGE)
                .hasMessageNotContaining("private provider comment")
                .hasMessageNotContaining("secret-token")
                .hasMessageNotContaining("private data")
                .hasMessageNotContaining("challenge");
        fixture.server().verify();
    }

    private ClientFixture clientFixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CodeforcesContestClient client = new CodeforcesContestClient(builder, properties(), false);
        return new ClientFixture(client, server);
    }

    private OpportunityProviderProperties properties() {
        OpportunityProviderProperties properties = new OpportunityProviderProperties();
        properties.getProviders().getCodeforces().setApiUrl(API_URL);
        return properties;
    }

    private record ClientFixture(CodeforcesContestClient client, MockRestServiceServer server) {
    }
}

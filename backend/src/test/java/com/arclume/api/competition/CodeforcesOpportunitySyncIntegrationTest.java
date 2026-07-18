package com.arclume.api.competition;

import com.arclume.api.client.CodeforcesContestClient;
import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.Competition;
import com.arclume.api.domain.CompetitionPhase;
import com.arclume.api.domain.OpportunitySyncStatus;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.User;
import com.arclume.api.repository.CompetitionRepository;
import com.arclume.api.repository.OpportunitySyncRunRepository;
import com.arclume.api.repository.UserRepository;
import com.arclume.api.security.SessionService;
import com.arclume.api.service.opportunity.OpportunityProvider;
import com.arclume.api.service.opportunity.OpportunityProviderException;
import com.arclume.api.service.opportunity.OpportunityProviderRegistry;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.opportunity.providers.codeforces.enabled=true",
        "app.opportunity.providers.codeforces.past-retention-days=14"
})
@AutoConfigureMockMvc
class CodeforcesOpportunitySyncIntegrationTest extends BaseIntegrationTest {

    private static final Instant START = Instant.parse("2026-08-01T12:00:00Z");
    private static final String LEAKY_COMMENT = "private Codeforces comment token=secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompetitionRepository competitionRepository;

    @Autowired
    private OpportunitySyncRunRepository opportunitySyncRunRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private OpportunityProviderRegistry providerRegistry;

    @MockitoBean
    private CodeforcesContestClient codeforcesContestClient;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        reset(codeforcesContestClient, clock);
        when(clock.instant()).thenReturn(Instant.parse("2026-07-18T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        opportunitySyncRunRepository.deleteAll();
        competitionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registryContainsAllProvidersInDeterministicOrderAndNormalizesCodeforcesLookup() {
        assertThat(providerRegistry.providers())
                .extracting(OpportunityProvider::providerKey)
                .containsExactly("CODEFORCES", "GREENHOUSE", "JOBICY", "LEVER", "REMOTIVE", "THE_MUSE");
        assertThat(providerRegistry.getRequired(" codeforces ").providerKey()).isEqualTo("CODEFORCES");
    }

    @Test
    void codeforcesSyncPreservesAdminAndCsrfRequirements() throws Exception {
        mockMvc.perform(post("/api/v1/admin/opportunity-sync/codeforces").with(csrf()))
                .andExpect(status().isUnauthorized());

        Cookie userCookie = createCookie("codeforces-user@example.com", Role.USER);
        mockMvc.perform(post("/api/v1/admin/opportunity-sync/codeforces")
                        .cookie(userCookie)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        Cookie adminCookie = createCookie("codeforces-missing-csrf@example.com", Role.ADMIN);
        mockMvc.perform(post("/api/v1/admin/opportunity-sync/codeforces")
                        .cookie(adminCookie))
                .andExpect(status().isForbidden());

        verifyNoInteractions(codeforcesContestClient);
    }

    @Test
    void lowercaseRoutePersistsCompetitionsAndSecondSyncUpdatesInsteadOfDuplicating() throws Exception {
        Cookie adminCookie = createCookie("codeforces-admin@example.com", Role.ADMIN);
        when(codeforcesContestClient.fetchContests())
                .thenReturn(List.of(contest(9001L, "Round 9001", "CF", "BEFORE", START.getEpochSecond(), 7200L)))
                .thenReturn(List.of(contest(9001L, "Round 9001 Updated", "CF", "FINISHED", START.getEpochSecond(), 7200L)));

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/codeforces")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerKey").value("CODEFORCES"))
                .andExpect(jsonPath("$.category").value("COMPETITION"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.recordsFetched").value(1))
                .andExpect(jsonPath("$.recordsCreated").value(1))
                .andExpect(jsonPath("$.recordsUpdated").value(0))
                .andExpect(jsonPath("$.recordsSkipped").value(0))
                .andExpect(jsonPath("$.recordsFailed").value(0))
                .andExpect(jsonPath("$.recordsDeactivated").value(0));

        assertThat(competitionRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getSourceProvider()).isEqualTo("CODEFORCES");
            assertThat(saved.getSourceId()).isEqualTo("9001");
            assertThat(saved.getExternalUrl()).isEqualTo("https://codeforces.com/contest/9001");
            assertThat(saved.getAttributionLabel()).isEqualTo("Contest data from Codeforces");
            assertThat(saved.getOrganizer()).isEqualTo("Codeforces");
            assertThat(saved.getStartsAt()).isEqualTo(START);
            assertThat(saved.getEndsAt()).isEqualTo(START.plusSeconds(7200));
            assertThat(saved.getDurationSeconds()).isEqualTo(7200L);
            assertThat(saved.getActive()).isTrue();
        });

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/CODEFORCES")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsCreated").value(0))
                .andExpect(jsonPath("$.recordsUpdated").value(1))
                .andExpect(jsonPath("$.recordsDeactivated").value(0));

        assertThat(competitionRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getTitle()).isEqualTo("Round 9001 Updated");
            assertThat(saved.getPhase()).isEqualTo(CompetitionPhase.FINISHED);
            assertThat(saved.getActive()).isFalse();
        });
    }

    @Test
    void invalidAndDuplicateRecordsIncrementSkippedCounterWithoutDeactivation() throws Exception {
        Cookie adminCookie = createCookie("codeforces-skipped@example.com", Role.ADMIN);
        when(codeforcesContestClient.fetchContests()).thenReturn(List.of(
                contest(1L, "Valid", "CF", "BEFORE", START.getEpochSecond(), 3600L),
                contest(1L, "Duplicate", "CF", "BEFORE", START.getEpochSecond(), 3600L),
                contest(2L, "Invalid Type", "UNKNOWN", "BEFORE", START.getEpochSecond(), 3600L)
        ));

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/CODEFORCES")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.recordsFetched").value(3))
                .andExpect(jsonPath("$.recordsCreated").value(1))
                .andExpect(jsonPath("$.recordsSkipped").value(2))
                .andExpect(jsonPath("$.recordsFailed").value(0))
                .andExpect(jsonPath("$.recordsDeactivated").value(0));

        assertThat(competitionRepository.findAll()).singleElement()
                .satisfies(saved -> assertThat(saved.getSourceId()).isEqualTo("1"));
    }

    @Test
    void providerFailureProducesDurableSafeFailedRunWithoutCommentLeakage() throws Exception {
        Cookie adminCookie = createCookie("codeforces-failed@example.com", Role.ADMIN);
        when(codeforcesContestClient.fetchContests())
                .thenThrow(new OpportunityProviderException("Failed to fetch contests from Codeforces API", new RuntimeException(LEAKY_COMMENT)));

        MvcResult result = mockMvc.perform(post("/api/v1/admin/opportunity-sync/CODEFORCES")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.providerKey").value("CODEFORCES"))
                .andExpect(jsonPath("$.category").value("COMPETITION"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.recordsFetched").value(0))
                .andExpect(jsonPath("$.recordsFailed").value(1))
                .andExpect(jsonPath("$.syncError").value("Provider sync failed: OpportunityProviderException"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(LEAKY_COMMENT);
        assertThat(opportunitySyncRunRepository.findAll()).singleElement().satisfies(run -> {
            assertThat(run.getProviderKey()).isEqualTo("CODEFORCES");
            assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.FAILED);
            assertThat(run.getSyncError()).isEqualTo("Provider sync failed: OpportunityProviderException");
            assertThat(run.getSyncError()).doesNotContain(LEAKY_COMMENT);
            assertThat(run.getRecordsDeactivated()).isZero();
        });
    }

    private Cookie createCookie(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPasswordHash("hashed");
        user.setRole(role);
        user = userRepository.saveAndFlush(user);
        String token = sessionService.create(user, "integration-test", "127.0.0.1").token();
        return new Cookie("ARCLUME_SESSION", token);
    }

    private CodeforcesContestClient.CodeforcesContest contest(
            Long id,
            String name,
            String type,
            String phase,
            Long startTimeSeconds,
            Long durationSeconds) {
        CodeforcesContestClient.CodeforcesContest contest = new CodeforcesContestClient.CodeforcesContest();
        contest.setId(id);
        contest.setName(name);
        contest.setType(type);
        contest.setPhase(phase);
        contest.setStartTimeSeconds(startTimeSeconds);
        contest.setDurationSeconds(durationSeconds);
        return contest;
    }
}

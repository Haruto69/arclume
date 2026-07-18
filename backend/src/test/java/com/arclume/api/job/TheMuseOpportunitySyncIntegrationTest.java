package com.arclume.api.job;

import com.arclume.api.client.TheMuseJobClient;
import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.Job;
import com.arclume.api.domain.OpportunityCategory;
import com.arclume.api.domain.OpportunitySyncStatus;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.User;
import com.arclume.api.domain.WorkMode;
import com.arclume.api.repository.JobRepository;
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

import java.time.Instant;
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
        "app.opportunity.providers.the-muse.enabled=true",
        "app.opportunity.providers.the-muse.api-key=integration-muse-key",
        "app.opportunity.providers.the-muse.max-pages=3"
})
@AutoConfigureMockMvc
class TheMuseOpportunitySyncIntegrationTest extends BaseIntegrationTest {

    private static final String API_KEY = "integration-muse-key";
    private static final String LEAKY_FAILURE_MESSAGE = "api_key=" + API_KEY
            + " provider response body with private data Authorization: Bearer secret-token";
    private static final String[] SECRET_VALUES = {
            API_KEY,
            "provider response body with private data",
            "secret-token"
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private OpportunitySyncRunRepository opportunitySyncRunRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private OpportunityProviderRegistry providerRegistry;

    @MockitoBean
    private TheMuseJobClient theMuseJobClient;

    @BeforeEach
    void setUp() {
        reset(theMuseJobClient);
        opportunitySyncRunRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registryContainsAllProvidersInDeterministicOrderAndNormalizesTheMuseLookup() {
        assertThat(providerRegistry.providers())
                .extracting(OpportunityProvider::providerKey)
                .containsExactly("CODEFORCES", "GREENHOUSE", "JOBICY", "LEVER", "REMOTIVE", "THE_MUSE");
        assertThat(providerRegistry.getRequired("the_muse").providerKey()).isEqualTo("THE_MUSE");
        assertThat(providerRegistry.getRequired(" THE_MUSE ").providerKey()).isEqualTo("THE_MUSE");
    }

    @Test
    void theMuseSyncPreservesAdminAndCsrfRequirements() throws Exception {
        mockMvc.perform(post("/api/v1/admin/opportunity-sync/the_muse").with(csrf()))
                .andExpect(status().isUnauthorized());

        Cookie userCookie = createCookie("muse-user@example.com", Role.USER);
        mockMvc.perform(post("/api/v1/admin/opportunity-sync/the_muse")
                        .cookie(userCookie)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        Cookie adminCookie = createCookie("muse-missing-csrf@example.com", Role.ADMIN);
        mockMvc.perform(post("/api/v1/admin/opportunity-sync/the_muse")
                        .cookie(adminCookie))
                .andExpect(status().isForbidden());

        verifyNoInteractions(theMuseJobClient);
    }

    @Test
    void lowercaseTheMuseRoutePersistsSanitizedJobsAndSecondSyncUpdatesInsteadOfDuplicating() throws Exception {
        Cookie adminCookie = createCookie("muse-admin@example.com", Role.ADMIN);
        TheMuseJobClient.TheMuseJob first = job(7001L, "Backend Engineer", "Arclume Labs",
                "https://www.themuse.com/jobs/arclume/backend-engineer?source=api#fragment");
        first.setPublicationDate("2026-07-18T10:15:30Z");
        first.setContents("<p>Build APIs</p><script>alert('x')</script><a href=\"javascript:alert(1)\">bad</a>");
        first.setLocations(List.of(named("Remote")));
        first.setLevels(List.of(named("Internship")));

        TheMuseJobClient.TheMuseJob second = job(7001L, "Senior Backend Engineer", "Arclume Labs",
                "https://api-v2.themuse.com/jobs/arclume/backend-engineer?source=api");
        second.setContents("<p>Build better APIs</p>");
        when(theMuseJobClient.fetchPage(0, API_KEY))
                .thenReturn(page(0, 1, List.of(first)))
                .thenReturn(page(0, 1, List.of(second)));

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/the_muse")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerKey").value("THE_MUSE"))
                .andExpect(jsonPath("$.category").value("JOB"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.recordsFetched").value(1))
                .andExpect(jsonPath("$.recordsCreated").value(1))
                .andExpect(jsonPath("$.recordsUpdated").value(0))
                .andExpect(jsonPath("$.recordsSkipped").value(0))
                .andExpect(jsonPath("$.recordsFailed").value(0))
                .andExpect(jsonPath("$.recordsDeactivated").value(0));

        assertThat(jobRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getSourceProvider()).isEqualTo("THE_MUSE");
            assertThat(saved.getExternalId()).isEqualTo("7001");
            assertThat(saved.getExternalUrl()).isEqualTo("https://www.themuse.com/jobs/arclume/backend-engineer?source=api");
            assertThat(saved.getAttributionLabel()).isEqualTo("Jobs provided by The Muse");
            assertThat(saved.getCompany()).isEqualTo("Arclume Labs");
            assertThat(saved.getLocation()).isEqualTo("Remote");
            assertThat(saved.getWorkMode()).isEqualTo(WorkMode.REMOTE);
            assertThat(saved.getEmploymentType()).isEqualTo(EmploymentType.INTERNSHIP);
            assertThat(saved.getSalaryRange()).isNull();
            assertThat(saved.getPostedAt()).isEqualTo(Instant.parse("2026-07-18T10:15:30Z"));
            assertThat(saved.getDescription()).contains("Build APIs", "bad");
            assertThat(saved.getDescription()).doesNotContain("script", "javascript:");
            assertThat(saved.getRequirements()).isNull();
            assertThat(saved.getActive()).isTrue();
        });

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/THE_MUSE")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsCreated").value(0))
                .andExpect(jsonPath("$.recordsUpdated").value(1))
                .andExpect(jsonPath("$.recordsDeactivated").value(0));

        assertThat(jobRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getSourceProvider()).isEqualTo("THE_MUSE");
            assertThat(saved.getExternalId()).isEqualTo("7001");
            assertThat(saved.getTitle()).isEqualTo("Senior Backend Engineer");
            assertThat(saved.getExternalUrl()).isEqualTo("https://www.themuse.com/jobs/arclume/backend-engineer?source=api");
            assertThat(saved.getDescription()).isEqualTo("<p>Build better APIs</p>");
        });
    }

    @Test
    void invalidTheMuseRecordsIncrementSkippedWithoutFailingRun() throws Exception {
        Cookie adminCookie = createCookie("muse-skipped@example.com", Role.ADMIN);
        when(theMuseJobClient.fetchPage(0, API_KEY)).thenReturn(page(0, 1, List.of(
                job(1L, "Bad Host", "Muse Co", "https://example.com/jobs/bad"),
                job(2L, "Valid", "Muse Co", "https://www.themuse.com/jobs/muse/valid")
        )));

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/THE_MUSE")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.recordsFetched").value(2))
                .andExpect(jsonPath("$.recordsCreated").value(1))
                .andExpect(jsonPath("$.recordsSkipped").value(1))
                .andExpect(jsonPath("$.recordsFailed").value(0))
                .andExpect(jsonPath("$.recordsDeactivated").value(0));

        assertThat(jobRepository.findAll()).singleElement()
                .satisfies(saved -> assertThat(saved.getExternalId()).isEqualTo("2"));
    }

    @Test
    void pageFailureCreatesSafeDurableFailedRunAndDoesNotPersistEarlierPage() throws Exception {
        Cookie adminCookie = createCookie("muse-failed@example.com", Role.ADMIN);
        when(theMuseJobClient.fetchPage(0, API_KEY))
                .thenReturn(page(0, 2, List.of(job(1L, "First Page", "Muse Co", "https://www.themuse.com/jobs/muse/first"))));
        when(theMuseJobClient.fetchPage(1, API_KEY))
                .thenThrow(new OpportunityProviderException(TheMuseJobClient.FAILURE_MESSAGE, new RuntimeException(LEAKY_FAILURE_MESSAGE)));

        MvcResult result = mockMvc.perform(post("/api/v1/admin/opportunity-sync/THE_MUSE")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.providerKey").value("THE_MUSE"))
                .andExpect(jsonPath("$.category").value("JOB"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.recordsFetched").value(0))
                .andExpect(jsonPath("$.recordsFailed").value(1))
                .andExpect(jsonPath("$.recordsDeactivated").value(0))
                .andExpect(jsonPath("$.syncError").value("Provider sync failed: OpportunityProviderException"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(SECRET_VALUES);
        assertThat(jobRepository.findAll()).isEmpty();
        assertThat(opportunitySyncRunRepository.findAll()).singleElement().satisfies(run -> {
            assertThat(run.getProviderKey()).isEqualTo("THE_MUSE");
            assertThat(run.getCategory()).isEqualTo(OpportunityCategory.JOB);
            assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.FAILED);
            assertThat(run.getSyncError()).isEqualTo("Provider sync failed: OpportunityProviderException");
            assertThat(run.getSyncError()).doesNotContain(SECRET_VALUES);
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

    private TheMuseJobClient.TheMuseJob job(Long id, String title, String companyName, String landingPage) {
        TheMuseJobClient.TheMuseJob job = new TheMuseJobClient.TheMuseJob();
        job.setId(id);
        job.setName(title);
        TheMuseJobClient.TheMuseCompany company = new TheMuseJobClient.TheMuseCompany();
        company.setId(100L);
        company.setName(companyName);
        company.setShortName("muse-co");
        job.setCompany(company);
        TheMuseJobClient.TheMuseRefs refs = new TheMuseJobClient.TheMuseRefs();
        refs.setLandingPage(landingPage);
        job.setRefs(refs);
        return job;
    }

    private TheMuseJobClient.TheMuseNamedValue named(String name) {
        TheMuseJobClient.TheMuseNamedValue value = new TheMuseJobClient.TheMuseNamedValue();
        value.setName(name);
        return value;
    }

    private TheMuseJobClient.TheMuseJobPage page(
            int page,
            int pageCount,
            List<TheMuseJobClient.TheMuseJob> jobs) {
        return new TheMuseJobClient.TheMuseJobPage(page, pageCount, jobs);
    }
}

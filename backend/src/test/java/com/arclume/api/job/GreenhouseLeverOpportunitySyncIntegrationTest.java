package com.arclume.api.job;

import com.arclume.api.client.GreenhouseJobClient;
import com.arclume.api.client.JobicyJobClient;
import com.arclume.api.client.LeverJobClient;
import com.arclume.api.client.RemotiveJobClient;
import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.config.OpportunityProviderProperties.LeverRegion;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.Job;
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

import java.math.BigDecimal;
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
        "app.opportunity.providers.greenhouse.enabled=true",
        "app.opportunity.providers.greenhouse.sources[0].enabled=true",
        "app.opportunity.providers.greenhouse.sources[0].board-token=green-alpha",
        "app.opportunity.providers.greenhouse.sources[0].company-name=Green Alpha Company",
        "app.opportunity.providers.greenhouse.sources[1].enabled=true",
        "app.opportunity.providers.greenhouse.sources[1].board-token=green-beta",
        "app.opportunity.providers.greenhouse.sources[1].company-name=Green Beta Company",
        "app.opportunity.providers.lever.enabled=true",
        "app.opportunity.providers.lever.sources[0].enabled=true",
        "app.opportunity.providers.lever.sources[0].site=lever-alpha",
        "app.opportunity.providers.lever.sources[0].company-name=Lever Alpha Company",
        "app.opportunity.providers.lever.sources[0].region=GLOBAL",
        "app.opportunity.providers.lever.sources[1].enabled=true",
        "app.opportunity.providers.lever.sources[1].site=lever-beta",
        "app.opportunity.providers.lever.sources[1].company-name=Lever Beta Company",
        "app.opportunity.providers.lever.sources[1].region=EU"
})
@AutoConfigureMockMvc
class GreenhouseLeverOpportunitySyncIntegrationTest extends BaseIntegrationTest {

    private static final String LEAKY_FAILURE_MESSAGE = "Authorization: Bearer actual-secret-token "
            + "token=token-secret https://provider.example/jobs?api_key=query-secret "
            + "provider response body with private data";

    private static final String[] SECRET_VALUES = {
            "actual-secret-token",
            "token-secret",
            "query-secret",
            "provider response body with private data"
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
    private GreenhouseJobClient greenhouseJobClient;

    @MockitoBean
    private LeverJobClient leverJobClient;

    @MockitoBean
    private RemotiveJobClient remotiveJobClient;

    @MockitoBean
    private JobicyJobClient jobicyJobClient;

    @BeforeEach
    void setUp() {
        reset(greenhouseJobClient, leverJobClient, remotiveJobClient, jobicyJobClient);
        opportunitySyncRunRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registryContainsAllJobProvidersInDeterministicOrderAndNormalizesLookup() {
        assertThat(providerRegistry.providers())
                .extracting(OpportunityProvider::providerKey)
                .containsExactly("CODEFORCES", "GREENHOUSE", "JOBICY", "LEVER", "REMOTIVE", "THE_MUSE");
        assertThat(providerRegistry.getRequired(" greenhouse ").providerKey()).isEqualTo("GREENHOUSE");
        assertThat(providerRegistry.getRequired("lever").providerKey()).isEqualTo("LEVER");
    }

    @Test
    void unauthenticatedUsersCannotTriggerGreenhouseSync() throws Exception {
        mockMvc.perform(post("/api/v1/admin/opportunity-sync/greenhouse").with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(greenhouseJobClient);
    }

    @Test
    void nonAdminUsersCannotTriggerLeverSync() throws Exception {
        Cookie userCookie = createCookie("lever-user@example.com", Role.USER);

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/lever")
                        .cookie(userCookie)
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(leverJobClient);
    }

    @Test
    void adminGreenhouseSyncRequiresCsrf() throws Exception {
        Cookie adminCookie = createCookie("greenhouse-missing-csrf@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/greenhouse")
                        .cookie(adminCookie))
                .andExpect(status().isForbidden());
        verifyNoInteractions(greenhouseJobClient);
    }

    @Test
    void lowercaseGreenhouseRoutePersistsJobsWithConfiguredSourceAttribution() throws Exception {
        Cookie adminCookie = createCookie("greenhouse-admin@example.com", Role.ADMIN);
        GreenhouseJobClient.GreenhouseJob job = greenhouseJob(
                "green-1",
                "Platform Engineer",
                "https://boards.greenhouse.io/fake/jobs/green-1");
        job.setContent("<p>Build APIs</p><script>alert('x')</script>");
        job.setLocation(greenhouseLocation("Remote"));
        when(greenhouseJobClient.fetchJobs("green-alpha")).thenReturn(List.of(job));
        when(greenhouseJobClient.fetchJobs("green-beta")).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/greenhouse")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerKey").value("GREENHOUSE"))
                .andExpect(jsonPath("$.category").value("JOB"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.recordsFetched").value(1))
                .andExpect(jsonPath("$.recordsCreated").value(1))
                .andExpect(jsonPath("$.recordsUpdated").value(0))
                .andExpect(jsonPath("$.recordsSkipped").value(0))
                .andExpect(jsonPath("$.recordsFailed").value(0))
                .andExpect(jsonPath("$.recordsDeactivated").value(0));

        assertThat(jobRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getSourceProvider()).isEqualTo("GREENHOUSE");
            assertThat(saved.getExternalId()).isEqualTo("green-alpha:green-1");
            assertThat(saved.getExternalUrl()).isEqualTo("https://boards.greenhouse.io/fake/jobs/green-1");
            assertThat(saved.getAttributionLabel()).isEqualTo("Job listing via Greenhouse");
            assertThat(saved.getCompany()).isEqualTo("Green Alpha Company");
            assertThat(saved.getLocation()).isEqualTo("Remote");
            assertThat(saved.getWorkMode()).isNull();
            assertThat(saved.getEmploymentType()).isNull();
            assertThat(saved.getSalaryRange()).isNull();
            assertThat(saved.getPostedAt()).isNull();
            assertThat(saved.getDescription()).isEqualTo("<p>Build APIs</p>");
            assertThat(saved.getActive()).isTrue();
        });
        assertThat(opportunitySyncRunRepository.findAll()).singleElement()
                .satisfies(run -> assertThat(run.getRecordsDeactivated()).isZero());
    }

    @Test
    void secondGreenhouseSyncUpdatesInsteadOfDuplicatingRecords() throws Exception {
        Cookie adminCookie = createCookie("greenhouse-idempotent@example.com", Role.ADMIN);
        when(greenhouseJobClient.fetchJobs("green-alpha"))
                .thenReturn(List.of(greenhouseJob("stable-id", "Engineer", "https://boards.greenhouse.io/fake/jobs/stable-id")))
                .thenReturn(List.of(greenhouseJob("stable-id", "Senior Engineer", "https://boards.greenhouse.io/fake/jobs/stable-id")));
        when(greenhouseJobClient.fetchJobs("green-beta")).thenReturn(List.of()).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/GREENHOUSE")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsCreated").value(1));
        mockMvc.perform(post("/api/v1/admin/opportunity-sync/GREENHOUSE")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsCreated").value(0))
                .andExpect(jsonPath("$.recordsUpdated").value(1));

        assertThat(jobRepository.findAll()).singleElement()
                .satisfies(job -> assertThat(job.getTitle()).isEqualTo("Senior Engineer"));
    }

    @Test
    void sameRawGreenhouseIdFromDifferentBoardsCreatesSeparateRecords() throws Exception {
        Cookie adminCookie = createCookie("greenhouse-source-separated@example.com", Role.ADMIN);
        when(greenhouseJobClient.fetchJobs("green-alpha"))
                .thenReturn(List.of(greenhouseJob("shared-id", "Alpha Engineer", "https://boards.greenhouse.io/fake/jobs/alpha")));
        when(greenhouseJobClient.fetchJobs("green-beta"))
                .thenReturn(List.of(greenhouseJob("shared-id", "Beta Engineer", "https://boards.greenhouse.io/fake/jobs/beta")));

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/GREENHOUSE")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsCreated").value(2));

        assertThat(jobRepository.findAll())
                .extracting(job -> job.getSourceProvider() + ":" + job.getExternalId())
                .containsExactlyInAnyOrder("GREENHOUSE:green-alpha:shared-id", "GREENHOUSE:green-beta:shared-id");
    }

    @Test
    void lowercaseLeverRoutePersistsJobsWithMappingsAndAttribution() throws Exception {
        Cookie adminCookie = createCookie("lever-admin@example.com", Role.ADMIN);
        LeverJobClient.LeverPosting posting = leverPosting("lever-1", "Backend Engineer", "https://jobs.lever.co/fake/lever-1");
        posting.setCategories(leverCategories("Remote", "full-time", List.of("Remote", "London")));
        posting.setWorkplaceType("hybrid");
        posting.setSalaryRange(leverSalaryRange("USD", "year", "80000", "90000"));
        posting.setDescription("<p>Build services</p><script>alert('x')</script>");
        when(leverJobClient.fetchJobs("lever-alpha", LeverRegion.GLOBAL)).thenReturn(List.of(posting));
        when(leverJobClient.fetchJobs("lever-beta", LeverRegion.EU)).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/lever")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerKey").value("LEVER"))
                .andExpect(jsonPath("$.category").value("JOB"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.recordsFetched").value(1))
                .andExpect(jsonPath("$.recordsCreated").value(1))
                .andExpect(jsonPath("$.recordsFailed").value(0))
                .andExpect(jsonPath("$.recordsDeactivated").value(0));

        assertThat(jobRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getSourceProvider()).isEqualTo("LEVER");
            assertThat(saved.getExternalId()).isEqualTo("global:lever-alpha:lever-1");
            assertThat(saved.getExternalUrl()).isEqualTo("https://jobs.lever.co/fake/lever-1");
            assertThat(saved.getAttributionLabel()).isEqualTo("Job listing via Lever");
            assertThat(saved.getCompany()).isEqualTo("Lever Alpha Company");
            assertThat(saved.getLocation()).isEqualTo("Remote, London");
            assertThat(saved.getWorkMode()).isEqualTo(WorkMode.HYBRID);
            assertThat(saved.getEmploymentType()).isEqualTo(EmploymentType.FULL_TIME);
            assertThat(saved.getSalaryRange()).isEqualTo("USD 80000 - 90000 / year");
            assertThat(saved.getPostedAt()).isNull();
            assertThat(saved.getDescription()).isEqualTo("<p>Build services</p>");
        });
    }

    @Test
    void secondLeverSyncUpdatesInsteadOfDuplicatingRecords() throws Exception {
        Cookie adminCookie = createCookie("lever-idempotent@example.com", Role.ADMIN);
        when(leverJobClient.fetchJobs("lever-alpha", LeverRegion.GLOBAL))
                .thenReturn(List.of(leverPosting("stable-id", "Engineer", "https://jobs.lever.co/fake/stable-id")))
                .thenReturn(List.of(leverPosting("stable-id", "Senior Engineer", "https://jobs.lever.co/fake/stable-id")));
        when(leverJobClient.fetchJobs("lever-beta", LeverRegion.EU)).thenReturn(List.of()).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/LEVER")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsCreated").value(1));
        mockMvc.perform(post("/api/v1/admin/opportunity-sync/LEVER")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsCreated").value(0))
                .andExpect(jsonPath("$.recordsUpdated").value(1));

        assertThat(jobRepository.findAll()).singleElement()
                .satisfies(job -> assertThat(job.getTitle()).isEqualTo("Senior Engineer"));
    }

    @Test
    void sameRawIdAcrossProvidersDoesNotCollide() throws Exception {
        Cookie adminCookie = createCookie("provider-separated@example.com", Role.ADMIN);
        jobRepository.saveAllAndFlush(List.of(
                storedJob("REMOTIVE", "shared-id", "Remotive Shared"),
                storedJob("JOBICY", "shared-id", "Jobicy Shared")
        ));
        when(greenhouseJobClient.fetchJobs("green-alpha"))
                .thenReturn(List.of(greenhouseJob("shared-id", "Greenhouse Shared", "https://boards.greenhouse.io/fake/jobs/shared-id")));
        when(greenhouseJobClient.fetchJobs("green-beta")).thenReturn(List.of());
        when(leverJobClient.fetchJobs("lever-alpha", LeverRegion.GLOBAL))
                .thenReturn(List.of(leverPosting("shared-id", "Lever Shared", "https://jobs.lever.co/fake/shared-id")));
        when(leverJobClient.fetchJobs("lever-beta", LeverRegion.EU)).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/GREENHOUSE")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsCreated").value(1));
        mockMvc.perform(post("/api/v1/admin/opportunity-sync/LEVER")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsCreated").value(1));

        assertThat(jobRepository.findAll())
                .extracting(job -> job.getSourceProvider() + ":" + job.getExternalId())
                .containsExactlyInAnyOrder(
                        "REMOTIVE:shared-id",
                        "JOBICY:shared-id",
                        "GREENHOUSE:green-alpha:shared-id",
                        "LEVER:global:lever-alpha:shared-id");
    }

    @Test
    void partialSourceFailurePersistsSuccessfulRecordsAndDurablePartialCounters() throws Exception {
        Cookie adminCookie = createCookie("greenhouse-partial@example.com", Role.ADMIN);
        when(greenhouseJobClient.fetchJobs("green-alpha"))
                .thenReturn(List.of(greenhouseJob("green-good", "Good Engineer", "https://boards.greenhouse.io/fake/jobs/good")));
        when(greenhouseJobClient.fetchJobs("green-beta"))
                .thenThrow(new OpportunityProviderException(LEAKY_FAILURE_MESSAGE, null));

        MvcResult result = mockMvc.perform(post("/api/v1/admin/opportunity-sync/GREENHOUSE")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerKey").value("GREENHOUSE"))
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.recordsFetched").value(1))
                .andExpect(jsonPath("$.recordsCreated").value(1))
                .andExpect(jsonPath("$.recordsFailed").value(1))
                .andExpect(jsonPath("$.syncError").value("1 opportunity records or sources failed to persist or fetch"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(SECRET_VALUES);
        assertThat(jobRepository.findAll()).hasSize(1);
        assertThat(opportunitySyncRunRepository.findAll()).singleElement().satisfies(run -> {
            assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.PARTIAL);
            assertThat(run.getRecordsFailed()).isEqualTo(1);
            assertThat(run.getRecordsDeactivated()).isZero();
            assertThat(run.getSyncError()).doesNotContain(SECRET_VALUES);
        });
    }

    @Test
    void providerWideSourceFailurePersistsSafeSummary() throws Exception {
        Cookie adminCookie = createCookie("lever-failed@example.com", Role.ADMIN);
        when(leverJobClient.fetchJobs("lever-alpha", LeverRegion.GLOBAL))
                .thenThrow(new OpportunityProviderException(LEAKY_FAILURE_MESSAGE, null));
        when(leverJobClient.fetchJobs("lever-beta", LeverRegion.EU))
                .thenThrow(new OpportunityProviderException(LEAKY_FAILURE_MESSAGE, null));

        MvcResult result = mockMvc.perform(post("/api/v1/admin/opportunity-sync/LEVER")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.providerKey").value("LEVER"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.recordsFetched").value(0))
                .andExpect(jsonPath("$.recordsFailed").value(1))
                .andExpect(jsonPath("$.syncError").value("Provider sync failed: OpportunityProviderException"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(SECRET_VALUES);
        assertThat(opportunitySyncRunRepository.findAll()).singleElement().satisfies(run -> {
            assertThat(run.getProviderKey()).isEqualTo("LEVER");
            assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.FAILED);
            assertThat(run.getSyncError()).isEqualTo("Provider sync failed: OpportunityProviderException");
            assertThat(run.getSyncError()).doesNotContain(SECRET_VALUES);
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

    private GreenhouseJobClient.GreenhouseJob greenhouseJob(String id, String title, String url) {
        GreenhouseJobClient.GreenhouseJob job = new GreenhouseJobClient.GreenhouseJob();
        job.setId(id);
        job.setTitle(title);
        job.setAbsoluteUrl(url);
        return job;
    }

    private GreenhouseJobClient.GreenhouseLocation greenhouseLocation(String name) {
        GreenhouseJobClient.GreenhouseLocation location = new GreenhouseJobClient.GreenhouseLocation();
        location.setName(name);
        return location;
    }

    private LeverJobClient.LeverPosting leverPosting(String id, String title, String hostedUrl) {
        LeverJobClient.LeverPosting posting = new LeverJobClient.LeverPosting();
        posting.setId(id);
        posting.setText(title);
        posting.setHostedUrl(hostedUrl);
        return posting;
    }

    private LeverJobClient.LeverCategories leverCategories(String location, String commitment, List<String> allLocations) {
        LeverJobClient.LeverCategories categories = new LeverJobClient.LeverCategories();
        categories.setLocation(location);
        categories.setCommitment(commitment);
        categories.setAllLocations(allLocations);
        return categories;
    }

    private LeverJobClient.LeverSalaryRange leverSalaryRange(String currency, String interval, String min, String max) {
        LeverJobClient.LeverSalaryRange salaryRange = new LeverJobClient.LeverSalaryRange();
        salaryRange.setCurrency(currency);
        salaryRange.setInterval(interval);
        if (min != null) {
            salaryRange.setMin(new BigDecimal(min));
        }
        if (max != null) {
            salaryRange.setMax(new BigDecimal(max));
        }
        return salaryRange;
    }

    private Job storedJob(String provider, String externalId, String title) {
        Job job = new Job();
        job.setSourceProvider(provider);
        job.setExternalId(externalId);
        job.setTitle(title);
        job.setCompany("Stored Company");
        return job;
    }
}

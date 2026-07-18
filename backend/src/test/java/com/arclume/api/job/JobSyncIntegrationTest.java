package com.arclume.api.job;

import com.arclume.api.client.RemotiveJobClient;
import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.Job;
import com.arclume.api.domain.OpportunitySyncStatus;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.User;
import com.arclume.api.domain.WorkMode;
import com.arclume.api.dto.JobSyncSummary;
import com.arclume.api.dto.OpportunitySyncResponse;
import com.arclume.api.repository.JobRepository;
import com.arclume.api.repository.OpportunitySyncRunRepository;
import com.arclume.api.repository.UserRepository;
import com.arclume.api.security.SessionService;
import com.arclume.api.service.JobSyncService;
import com.arclume.api.service.opportunity.OpportunityProviderException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.opportunity.providers.remotive.enabled=true"
})
@AutoConfigureMockMvc
class JobSyncIntegrationTest extends BaseIntegrationTest {

    private static final String LEAKY_FAILURE_MESSAGE = "Authorization: Bearer actual-secret-token "
            + "Bearer standalone-secret-token api_key=api-secret apiKey=camel-secret "
            + "token=token-secret access_token=access-secret password=password-secret "
            + "Cookie: session=cookie-secret Set-Cookie: refresh=set-cookie-secret "
            + "https://provider.example/jobs?api_key=query-secret&access_token=query-token "
            + "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signaturepart "
            + "provider response body with private data";

    private static final String[] SECRET_VALUES = {
            "actual-secret-token",
            "standalone-secret-token",
            "api-secret",
            "camel-secret",
            "token-secret",
            "access-secret",
            "password-secret",
            "cookie-secret",
            "set-cookie-secret",
            "query-secret",
            "query-token",
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signaturepart",
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
    private JobSyncService jobSyncService;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private RemotiveJobClient jobClient;

    @Autowired
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        opportunitySyncRunRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void schedulerIsDisabledByDefault() {
        boolean hasScheduler = applicationContext.containsBean("jobSyncScheduler");
        assertThat(hasScheduler).isFalse();
    }

    @Test
    void syncPerformsInsertUpdateAndDeduplication() {
        RemotiveJobClient.RemotiveJob job = remotiveJob(
                "remotive-1",
                "Software Engineer",
                "Tech Co",
                "https://remotive.com/job1"
        );
        job.setJobType("full_time");
        job.setDescription("<p>HTML description</p><script>alert('xss')</script><img src=x onerror=alert(1)>");
        job.setCandidateRequiredLocation("USA");
        when(jobClient.fetchJobs()).thenReturn(List.of(job));

        JobSyncSummary summary1 = jobSyncService.syncJobs();
        assertThat(summary1.getFetchedCount()).isEqualTo(1);
        assertThat(summary1.getInsertedCount()).isEqualTo(1);
        assertThat(summary1.getUpdatedCount()).isEqualTo(0);

        List<Job> savedJobs = jobRepository.findAll();
        assertThat(savedJobs).hasSize(1);
        Job savedJob = savedJobs.get(0);
        assertThat(savedJob.getTitle()).isEqualTo("Software Engineer");
        assertThat(savedJob.getDescription()).isEqualTo("<p>HTML description</p>");
        assertThat(savedJob.getWorkMode()).isEqualTo(WorkMode.REMOTE);
        assertThat(savedJob.getEmploymentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(savedJob.getSourceProvider()).isEqualTo("REMOTIVE");
        assertThat(savedJob.getExternalUrl()).isEqualTo("https://remotive.com/job1");
        assertThat(savedJob.getAttributionLabel()).isEqualTo("Jobs provided by Remotive");
        assertThat(savedJob.getSyncedAt()).isNotNull();

        job.setTitle("Senior Software Engineer");
        JobSyncSummary summary2 = jobSyncService.syncJobs();
        assertThat(summary2.getFetchedCount()).isEqualTo(1);
        assertThat(summary2.getInsertedCount()).isEqualTo(0);
        assertThat(summary2.getUpdatedCount()).isEqualTo(1);

        savedJobs = jobRepository.findAll();
        assertThat(savedJobs).hasSize(1);
        assertThat(savedJobs.get(0).getTitle()).isEqualTo("Senior Software Engineer");
    }

    @Test
    void malformedRecordsAreSkippedSafely() {
        RemotiveJobClient.RemotiveJob badJob = new RemotiveJobClient.RemotiveJob();
        badJob.setId("remotive-bad");
        badJob.setCompanyName("Tech Co");

        RemotiveJobClient.RemotiveJob goodJob = remotiveJob(
                "remotive-good",
                "Backend Developer",
                "Backend Co",
                "https://remotive.com/backend"
        );
        when(jobClient.fetchJobs()).thenReturn(List.of(badJob, goodJob));

        JobSyncSummary summary = jobSyncService.syncJobs();
        assertThat(summary.getFetchedCount()).isEqualTo(2);
        assertThat(summary.getInsertedCount()).isEqualTo(1);
        assertThat(summary.getSkippedCount()).isEqualTo(1);
        assertThat(summary.getFailedCount()).isEqualTo(0);
    }

    @Test
    void perRecordPersistenceFailureProducesPartialSyncRun() {
        RemotiveJobClient.RemotiveJob goodJob = remotiveJob(
                "remotive-good",
                "Backend Developer",
                "Backend Co",
                "https://remotive.com/backend"
        );
        RemotiveJobClient.RemotiveJob badJob = remotiveJob(
                "remotive-too-long",
                "Oversized Company Job",
                "A".repeat(201),
                "https://remotive.com/oversized"
        );
        when(jobClient.fetchJobs()).thenReturn(List.of(goodJob, badJob));

        OpportunitySyncResponse response = jobSyncService.syncRemotive();

        assertThat(response.status()).isEqualTo(OpportunitySyncStatus.PARTIAL);
        assertThat(response.recordsFetched()).isEqualTo(2);
        assertThat(response.recordsCreated()).isEqualTo(1);
        assertThat(response.recordsFailed()).isEqualTo(1);
        assertThat(jobRepository.findAll()).hasSize(1);
        assertThat(opportunitySyncRunRepository.findAll())
                .singleElement()
                .satisfies(run -> {
                    assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.PARTIAL);
                    assertThat(run.getFinishedAt()).isNotNull();
                    assertThat(run.getSyncError()).contains("failed to persist");
                });
    }

    @Test
    void clientFailureOrTimeoutIsHandledSafelyAndCreatesDurableRun() {
        when(jobClient.fetchJobs()).thenThrow(new OpportunityProviderException(
                LEAKY_FAILURE_MESSAGE + " " + "x".repeat(1200), null));

        OpportunitySyncResponse response = jobSyncService.syncRemotive();

        assertThat(response.status()).isEqualTo(OpportunitySyncStatus.FAILED);
        assertThat(response.recordsFailed()).isEqualTo(1);
        assertThat(response.recordsFetched()).isEqualTo(0);
        assertThat(response.syncError()).isEqualTo("Provider sync failed: OpportunityProviderException");
        assertThat(response.syncError()).doesNotContain(SECRET_VALUES);
        assertThat(response.syncError()).hasSizeLessThanOrEqualTo(1000);
        assertThat(opportunitySyncRunRepository.findAll())
                .singleElement()
                .satisfies(run -> {
                    assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.FAILED);
                    assertThat(run.getSyncError()).isEqualTo("Provider sync failed: OpportunityProviderException");
                    assertThat(run.getSyncError()).doesNotContain(SECRET_VALUES);
                    assertThat(run.getFinishedAt()).isNotNull();
                });
    }

    @Test
    void searchAndPaginationWorksWithFilters() throws Exception {
        Cookie userCookie = createCookie("user@example.com", Role.USER);

        Job j1 = new Job();
        j1.setTitle("Java Engineer");
        j1.setCompany("Arclume Co");
        j1.setLocation("Remote");
        j1.setWorkMode(WorkMode.REMOTE);
        j1.setEmploymentType(EmploymentType.FULL_TIME);
        jobRepository.save(j1);

        Job j2 = new Job();
        j2.setTitle("React Engineer");
        j2.setCompany("Frontend Co");
        j2.setLocation("New York");
        j2.setWorkMode(WorkMode.ON_SITE);
        j2.setEmploymentType(EmploymentType.PART_TIME);
        jobRepository.save(j2);

        mockMvc.perform(get("/api/v1/jobs")
                        .cookie(userCookie)
                        .param("title", "java"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Job-Attribution"));
    }

    @Test
    void manualSyncAnonymousRejection() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/sync").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void manualSyncNonAdminRejection() throws Exception {
        Cookie userCookie = createCookie("user-sync@example.com", Role.USER);

        mockMvc.perform(post("/api/v1/jobs/sync")
                        .cookie(userCookie)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void manualSyncAdminRequiresCsrf() throws Exception {
        Cookie adminCookie = createCookie("admin-missing-csrf@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/v1/jobs/sync")
                        .cookie(adminCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void manualSyncAdminSuccess() throws Exception {
        Cookie adminCookie = createCookie("admin-sync@example.com", Role.ADMIN);

        when(jobClient.fetchJobs()).thenReturn(new ArrayList<>());
        mockMvc.perform(post("/api/v1/jobs/sync")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetchedCount").value(0))
                .andExpect(jsonPath("$.insertedCount").value(0))
                .andExpect(jsonPath("$.updatedCount").value(0))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.failedCount").value(0));
    }

    @Test
    void manualSyncAdminProviderFailureReturnsBadGateway() throws Exception {
        Cookie adminCookie = createCookie("admin-sync-failure@example.com", Role.ADMIN);
        when(jobClient.fetchJobs()).thenThrow(new OpportunityProviderException(LEAKY_FAILURE_MESSAGE, null));

        MvcResult result = mockMvc.perform(post("/api/v1/jobs/sync")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.fetchedCount").value(0))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(SECRET_VALUES);
        assertThat(opportunitySyncRunRepository.findAll())
                .singleElement()
                .satisfies(run -> assertThat(run.getSyncError())
                        .isEqualTo("Provider sync failed: OpportunityProviderException"));
    }

    @Test
    void adminProviderSyncSuccess() throws Exception {
        Cookie adminCookie = createCookie("admin-provider-sync@example.com", Role.ADMIN);

        when(jobClient.fetchJobs()).thenReturn(new ArrayList<>());
        mockMvc.perform(post("/api/v1/admin/opportunity-sync/remotive")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerKey").value("REMOTIVE"))
                .andExpect(jsonPath("$.category").value("JOB"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.recordsFetched").value(0))
                .andExpect(jsonPath("$.recordsCreated").value(0));
    }

    @Test
    void adminProviderSyncFailureRedactsResponseAndPersistedRun() throws Exception {
        Cookie adminCookie = createCookie("admin-provider-failure@example.com", Role.ADMIN);
        when(jobClient.fetchJobs()).thenThrow(new OpportunityProviderException(LEAKY_FAILURE_MESSAGE, null));

        MvcResult result = mockMvc.perform(post("/api/v1/admin/opportunity-sync/remotive")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.providerKey").value("REMOTIVE"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.recordsFetched").value(0))
                .andExpect(jsonPath("$.recordsFailed").value(1))
                .andExpect(jsonPath("$.syncError").value("Provider sync failed: OpportunityProviderException"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(SECRET_VALUES);
        assertThat(opportunitySyncRunRepository.findAll())
                .singleElement()
                .satisfies(run -> {
                    assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.FAILED);
                    assertThat(run.getSyncError()).isEqualTo("Provider sync failed: OpportunityProviderException");
                    assertThat(run.getSyncError()).doesNotContain(SECRET_VALUES);
                });
    }

    @Test
    void adminProviderSyncUnknownProviderIsStable() throws Exception {
        Cookie adminCookie = createCookie("admin-unknown-provider@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/unknown")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PROVIDER"));
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

    private RemotiveJobClient.RemotiveJob remotiveJob(String id, String title, String company, String url) {
        RemotiveJobClient.RemotiveJob job = new RemotiveJobClient.RemotiveJob();
        job.setId(id);
        job.setTitle(title);
        job.setCompanyName(company);
        job.setUrl(url);
        return job;
    }
}

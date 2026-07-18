package com.arclume.api.job;

import com.arclume.api.client.JobicyJobClient;
import com.arclume.api.client.RemotiveJobClient;
import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.Job;
import com.arclume.api.domain.OpportunityCategory;
import com.arclume.api.domain.OpportunitySyncStatus;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.User;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.opportunity.providers.jobicy.enabled=true"
})
@AutoConfigureMockMvc
class JobicyOpportunitySyncIntegrationTest extends BaseIntegrationTest {

    private static final String LEAKY_FAILURE_MESSAGE = "Authorization: Bearer actual-secret-token "
            + "token=token-secret Cookie: session=cookie-secret "
            + "https://provider.example/jobs?api_key=query-secret provider response body with private data";

    private static final String[] SECRET_VALUES = {
            "actual-secret-token",
            "token-secret",
            "cookie-secret",
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
    private JobicyJobClient jobicyJobClient;

    @MockitoBean
    private RemotiveJobClient remotiveJobClient;

    @BeforeEach
    void setUp() {
        opportunitySyncRunRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registryContainsJobicyAndRemotiveInDeterministicOrder() {
        assertThat(providerRegistry.providers())
                .extracting(OpportunityProvider::providerKey)
                .contains("JOBICY", "REMOTIVE");
        assertThat(providerRegistry.providers())
                .extracting(OpportunityProvider::providerKey)
                .containsSubsequence("JOBICY", "REMOTIVE");
        assertThat(providerRegistry.getRequired(" jobicy ").providerKey()).isEqualTo("JOBICY");
    }

    @Test
    void unauthenticatedUsersCannotTriggerJobicySync() throws Exception {
        mockMvc.perform(post("/api/v1/admin/opportunity-sync/jobicy").with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(jobicyJobClient);
    }

    @Test
    void nonAdminUsersCannotTriggerJobicySync() throws Exception {
        Cookie userCookie = createCookie("jobicy-user@example.com", Role.USER);

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/jobicy")
                        .cookie(userCookie)
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(jobicyJobClient);
    }

    @Test
    void adminJobicySyncRequiresCsrf() throws Exception {
        Cookie adminCookie = createCookie("jobicy-missing-csrf@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/jobicy")
                        .cookie(adminCookie))
                .andExpect(status().isForbidden());
        verifyNoInteractions(jobicyJobClient);
    }

    @Test
    void lowerCaseRouteInputSyncsJobicyAndPersistsRunMetadata() throws Exception {
        Cookie adminCookie = createCookie("jobicy-admin@example.com", Role.ADMIN);
        JobicyJobClient.JobicyJob job = jobicyJob("jobicy-1", "Java Developer", "Arclume Labs");
        job.setJobType("full-time");
        job.setJobGeo("Worldwide");
        job.setSalaryMin(new BigDecimal("50000"));
        job.setSalaryMax(new BigDecimal("70000"));
        job.setSalaryCurrency("USD");
        job.setSalaryPeriod("year");
        job.setPubDate("2026-07-18T10:15:30Z");
        job.setJobDescription("<p>Build APIs</p><script>alert('x')</script>");
        when(jobicyJobClient.fetchJobs()).thenReturn(List.of(job));

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/jobicy")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerKey").value("JOBICY"))
                .andExpect(jsonPath("$.category").value("JOB"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.recordsFetched").value(1))
                .andExpect(jsonPath("$.recordsCreated").value(1))
                .andExpect(jsonPath("$.recordsUpdated").value(0))
                .andExpect(jsonPath("$.recordsSkipped").value(0))
                .andExpect(jsonPath("$.recordsFailed").value(0))
                .andExpect(jsonPath("$.recordsDeactivated").value(0));

        assertThat(jobRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getSourceProvider()).isEqualTo("JOBICY");
            assertThat(saved.getExternalId()).isEqualTo("jobicy-1");
            assertThat(saved.getExternalUrl()).isEqualTo("https://jobicy.com/jobs/jobicy-1");
            assertThat(saved.getAttributionLabel()).isEqualTo("Jobs provided by Jobicy");
            assertThat(saved.getSalaryRange()).isEqualTo("USD 50000 - 70000 / year");
            assertThat(saved.getDescription()).isEqualTo("<p>Build APIs</p>");
            assertThat(saved.getSyncedAt()).isNotNull();
        });
        assertThat(opportunitySyncRunRepository.findAll()).singleElement().satisfies(run -> {
            assertThat(run.getProviderKey()).isEqualTo("JOBICY");
            assertThat(run.getCategory()).isEqualTo(OpportunityCategory.JOB);
            assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.SUCCEEDED);
            assertThat(run.getStartedAt()).isNotNull();
            assertThat(run.getFinishedAt()).isNotNull();
            assertThat(run.getRecordsFetched()).isEqualTo(1);
            assertThat(run.getRecordsCreated()).isEqualTo(1);
            assertThat(run.getRecordsDeactivated()).isZero();
        });
    }

    @Test
    void secondJobicySyncUpdatesInsteadOfDuplicatingRecords() throws Exception {
        Cookie adminCookie = createCookie("jobicy-idempotent@example.com", Role.ADMIN);
        JobicyJobClient.JobicyJob first = jobicyJob("jobicy-stable", "Java Developer", "Arclume Labs");
        JobicyJobClient.JobicyJob second = jobicyJob("jobicy-stable", "Senior Java Developer", "Arclume Labs");
        when(jobicyJobClient.fetchJobs()).thenReturn(List.of(first)).thenReturn(List.of(second));

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/JOBICY")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsCreated").value(1));
        mockMvc.perform(post("/api/v1/admin/opportunity-sync/JOBICY")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsCreated").value(0))
                .andExpect(jsonPath("$.recordsUpdated").value(1));

        assertThat(jobRepository.findAll()).singleElement()
                .satisfies(job -> assertThat(job.getTitle()).isEqualTo("Senior Java Developer"));
    }

    @Test
    void jobicyAndRemotiveCanUseSameExternalIdWithoutCrossProviderDeduplication() throws Exception {
        Cookie adminCookie = createCookie("jobicy-shared-id@example.com", Role.ADMIN);
        Job remotive = new Job();
        remotive.setTitle("Remote Developer");
        remotive.setCompany("Remote Co");
        remotive.setSourceProvider("REMOTIVE");
        remotive.setExternalId("shared-id");
        jobRepository.saveAndFlush(remotive);

        when(jobicyJobClient.fetchJobs()).thenReturn(List.of(jobicyJob("shared-id", "Jobicy Developer", "Jobicy Co")));

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/JOBICY")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsCreated").value(1));

        assertThat(jobRepository.findAll())
                .extracting(Job::getSourceProvider)
                .containsExactlyInAnyOrder("REMOTIVE", "JOBICY");
    }

    @Test
    void invalidJobicyRecordsAreSkippedWithoutFailingTheRun() throws Exception {
        Cookie adminCookie = createCookie("jobicy-skipped@example.com", Role.ADMIN);
        JobicyJobClient.JobicyJob missingUrl = new JobicyJobClient.JobicyJob();
        missingUrl.setId("missing-url");
        missingUrl.setJobTitle("Developer");
        missingUrl.setCompanyName("Missing Co");
        when(jobicyJobClient.fetchJobs()).thenReturn(List.of(missingUrl, jobicyJob("valid", "Developer", "Valid Co")));

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/JOBICY")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.recordsFetched").value(2))
                .andExpect(jsonPath("$.recordsCreated").value(1))
                .andExpect(jsonPath("$.recordsSkipped").value(1))
                .andExpect(jsonPath("$.recordsFailed").value(0));
    }

    @Test
    void perRecordPersistenceFailureProducesPartialJobicyRun() throws Exception {
        Cookie adminCookie = createCookie("jobicy-partial@example.com", Role.ADMIN);
        JobicyJobClient.JobicyJob good = jobicyJob("good", "Developer", "Good Co");
        JobicyJobClient.JobicyJob bad = jobicyJob("bad", "Developer", "A".repeat(201));
        when(jobicyJobClient.fetchJobs()).thenReturn(List.of(good, bad));

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/JOBICY")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.recordsFetched").value(2))
                .andExpect(jsonPath("$.recordsCreated").value(1))
                .andExpect(jsonPath("$.recordsFailed").value(1))
                .andExpect(jsonPath("$.recordsDeactivated").value(0));

        assertThat(jobRepository.findAll()).hasSize(1);
        assertThat(opportunitySyncRunRepository.findAll()).singleElement()
                .satisfies(run -> assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.PARTIAL));
    }

    @Test
    void providerWideJobicyFailurePersistsSafeSummary() throws Exception {
        Cookie adminCookie = createCookie("jobicy-failed@example.com", Role.ADMIN);
        when(jobicyJobClient.fetchJobs()).thenThrow(new OpportunityProviderException(LEAKY_FAILURE_MESSAGE, null));

        MvcResult result = mockMvc.perform(post("/api/v1/admin/opportunity-sync/JOBICY")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.providerKey").value("JOBICY"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.recordsFetched").value(0))
                .andExpect(jsonPath("$.recordsFailed").value(1))
                .andExpect(jsonPath("$.syncError").value("Provider sync failed: OpportunityProviderException"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(SECRET_VALUES);
        assertThat(opportunitySyncRunRepository.findAll()).singleElement().satisfies(run -> {
            assertThat(run.getProviderKey()).isEqualTo("JOBICY");
            assertThat(run.getCategory()).isEqualTo(OpportunityCategory.JOB);
            assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.FAILED);
            assertThat(run.getSyncError()).isEqualTo("Provider sync failed: OpportunityProviderException");
            assertThat(run.getSyncError()).doesNotContain(SECRET_VALUES);
            assertThat(run.getFinishedAt()).isNotNull();
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

    private JobicyJobClient.JobicyJob jobicyJob(String id, String title, String company) {
        JobicyJobClient.JobicyJob job = new JobicyJobClient.JobicyJob();
        job.setId(id);
        job.setUrl("https://jobicy.com/jobs/" + id);
        job.setJobTitle(title);
        job.setCompanyName(company);
        return job;
    }
}

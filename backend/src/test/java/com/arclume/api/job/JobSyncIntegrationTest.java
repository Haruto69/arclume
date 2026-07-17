package com.arclume.api.job;

import com.arclume.api.client.JobClient;
import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.Job;
import com.arclume.api.domain.WorkMode;
import com.arclume.api.dto.JobSyncSummary;
import com.arclume.api.repository.JobRepository;
import com.arclume.api.service.JobSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.arclume.api.repository.UserRepository;
import com.arclume.api.security.SessionService;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JobSyncIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobSyncService jobSyncService;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private JobClient jobClient;

    @Autowired
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
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
        List<JobClient.RemotiveJob> jobs = new ArrayList<>();
        
        JobClient.RemotiveJob job1 = new JobClient.RemotiveJob();
        job1.setId("remotive-1");
        job1.setTitle("Software Engineer");
        job1.setCompanyName("Tech Co");
        job1.setJobType("full_time");
        job1.setUrl("https://remotive.com/job1");
        job1.setDescription("<p>HTML description</p><script>alert('xss')</script><img src=x onerror=alert(1)>");
        job1.setCandidateRequiredLocation("USA");
        jobs.add(job1);

        when(jobClient.fetchJobs()).thenReturn(jobs);

        // 1. Insert
        JobSyncSummary summary1 = jobSyncService.syncJobs();
        assertThat(summary1.getFetchedCount()).isEqualTo(1);
        assertThat(summary1.getInsertedCount()).isEqualTo(1);
        assertThat(summary1.getUpdatedCount()).isEqualTo(0);

        List<Job> savedJobs = jobRepository.findAll();
        assertThat(savedJobs).hasSize(1);
        Job savedJob = savedJobs.get(0);
        assertThat(savedJob.getTitle()).isEqualTo("Software Engineer");
        // Verify HTML tags are preserved but dangerous scripts/event handlers are completely stripped
        assertThat(savedJob.getDescription()).isEqualTo("<p>HTML description</p>");
        assertThat(savedJob.getWorkMode()).isEqualTo(WorkMode.REMOTE);
        assertThat(savedJob.getEmploymentType()).isEqualTo(EmploymentType.FULL_TIME);

        // 2. Update
        job1.setTitle("Senior Software Engineer");
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
        List<JobClient.RemotiveJob> jobs = new ArrayList<>();
        
        // Malformed record (missing title)
        JobClient.RemotiveJob badJob = new JobClient.RemotiveJob();
        badJob.setId("remotive-bad");
        badJob.setCompanyName("Tech Co");
        jobs.add(badJob);

        // Valid record
        JobClient.RemotiveJob goodJob = new JobClient.RemotiveJob();
        goodJob.setId("remotive-good");
        goodJob.setTitle("Backend Developer");
        goodJob.setCompanyName("Backend Co");
        jobs.add(goodJob);

        when(jobClient.fetchJobs()).thenReturn(jobs);

        JobSyncSummary summary = jobSyncService.syncJobs();
        assertThat(summary.getFetchedCount()).isEqualTo(2);
        assertThat(summary.getInsertedCount()).isEqualTo(1);
        assertThat(summary.getSkippedCount()).isEqualTo(1);
    }

    @Test
    void clientFailureOrTimeoutIsHandledSafely() {
        when(jobClient.fetchJobs()).thenThrow(new RuntimeException("Timeout or 500 error"));

        JobSyncSummary summary = jobSyncService.syncJobs();
        assertThat(summary.getFailedCount()).isEqualTo(1);
        assertThat(summary.getFetchedCount()).isEqualTo(0);
    }

    @Test
    void searchAndPaginationWorksWithFilters() throws Exception {
        // Setup User cookie
        com.arclume.api.domain.User user = new com.arclume.api.domain.User();
        user.setEmail("user@example.com");
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setPasswordHash("hashed");
        user.setRole(com.arclume.api.domain.Role.USER);
        user = userRepository.saveAndFlush(user);

        String userToken = sessionService.create(user, "integration-test", "127.0.0.1").token();
        jakarta.servlet.http.Cookie userCookie = new jakarta.servlet.http.Cookie("ARCLUME_SESSION", userToken);

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
                .andExpect(status().isOk());
    }

    @Test
    void manualSyncAnonymousRejection() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/sync").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void manualSyncNonAdminRejection() throws Exception {
        // Setup Non-Admin User cookie
        com.arclume.api.domain.User user = new com.arclume.api.domain.User();
        user.setEmail("user-sync@example.com");
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setPasswordHash("hashed");
        user.setRole(com.arclume.api.domain.Role.USER);
        user = userRepository.saveAndFlush(user);

        String userToken = sessionService.create(user, "integration-test", "127.0.0.1").token();
        jakarta.servlet.http.Cookie userCookie = new jakarta.servlet.http.Cookie("ARCLUME_SESSION", userToken);

        mockMvc.perform(post("/api/v1/jobs/sync")
                        .cookie(userCookie)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void manualSyncAdminSuccess() throws Exception {
        // Setup Admin User cookie
        com.arclume.api.domain.User admin = new com.arclume.api.domain.User();
        admin.setEmail("admin-sync@example.com");
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setPasswordHash("hashed");
        admin.setRole(com.arclume.api.domain.Role.ADMIN);
        admin = userRepository.saveAndFlush(admin);

        String adminToken = sessionService.create(admin, "integration-test", "127.0.0.1").token();
        jakarta.servlet.http.Cookie adminCookie = new jakarta.servlet.http.Cookie("ARCLUME_SESSION", adminToken);

        when(jobClient.fetchJobs()).thenReturn(new ArrayList<>());
        mockMvc.perform(post("/api/v1/jobs/sync")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk());
    }
}

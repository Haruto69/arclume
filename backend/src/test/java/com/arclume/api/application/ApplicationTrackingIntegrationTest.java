package com.arclume.api.application;

import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.Application;
import com.arclume.api.domain.ApplicationStatus;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.Job;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.User;
import com.arclume.api.domain.WorkMode;
import com.arclume.api.repository.ApplicationRepository;
import com.arclume.api.repository.JobRecommendationRepository;
import com.arclume.api.repository.JobRepository;
import com.arclume.api.repository.ResumeRepository;
import com.arclume.api.repository.UserRepository;
import com.arclume.api.repository.UserSkillRepository;
import com.arclume.api.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.ai.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class ApplicationTrackingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private JobRecommendationRepository recommendationRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private JwtService jwtService;

    private User primaryUser;
    private User secondaryUser;
    private Cookie primaryCookie;
    private Cookie secondaryCookie;
    private Job backendJob;
    private Job frontendJob;

    @BeforeEach
    void setUp() {
        recommendationRepository.deleteAll();
        applicationRepository.deleteAll();
        userSkillRepository.deleteAll();
        resumeRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();

        primaryUser = user("alice-applications@example.com");
        secondaryUser = user("bob-applications@example.com");
        primaryCookie = authCookie(primaryUser);
        secondaryCookie = authCookie(secondaryUser);
        backendJob = job("Backend Engineer", "Arclume", WorkMode.REMOTE);
        frontendJob = job("Frontend Engineer", "Lumina", WorkMode.HYBRID);
    }

    @Test
    void authenticatedUserCanTrackJobWithSavedDefaultAndDuplicateIsIdempotent() throws Exception {
        String request = objectMapper.writeValueAsString(Map.of("jobId", backendJob.getId()));

        String firstResponse = mockMvc.perform(post("/api/v1/applications")
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").value(backendJob.getId().toString()))
                .andExpect(jsonPath("$.jobTitle").value("Backend Engineer"))
                .andExpect(jsonPath("$.companyName").value("Arclume"))
                .andExpect(jsonPath("$.status").value("SAVED"))
                .andExpect(jsonPath("$.sourceProvider").value("REMOTIVE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID applicationId = UUID.fromString(objectMapper.readTree(firstResponse).get("id").asText());

        mockMvc.perform(post("/api/v1/applications")
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(applicationId.toString()))
                .andExpect(jsonPath("$.status").value("SAVED"));

        mockMvc.perform(post("/api/v1/applications")
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", backendJob.getId(),
                                "status", "APPLIED"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(applicationId.toString()))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.appliedAt").isNotEmpty());
        mockMvc.perform(patch("/api/v1/applications/" + applicationId)
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INTERVIEWING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEWING"));

        mockMvc.perform(post("/api/v1/applications")
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", backendJob.getId(),
                                "status", "APPLIED"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(applicationId.toString()))
                .andExpect(jsonPath("$.status").value("INTERVIEWING"));
        assertThat(applicationRepository.countByUserId(primaryUser.getId())).isOne();
        assertThat(applicationRepository.findById(applicationId).orElseThrow().getAppliedAt()).isNotNull();
    }

    @Test
    void appliedStatusAcceptsExplicitAppliedDate() throws Exception {
        Instant appliedAt = Instant.parse("2026-07-17T09:00:00Z");

        mockMvc.perform(post("/api/v1/applications")
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", backendJob.getId(),
                                "status", "APPLIED",
                                "appliedAt", appliedAt.toString(),
                                "notes", "Applied through company portal."
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.appliedAt").value(appliedAt.toString()))
                .andExpect(jsonPath("$.notes").value("Applied through company portal."));

        Application application = applicationRepository
                .findByUserIdAndJobId(primaryUser.getId(), backendJob.getId())
                .orElseThrow();
        assertThat(application.getAppliedAt()).isEqualTo(appliedAt);
    }

    @Test
    void updateChangesStatusNotesAndCanSetOrClearAppliedDate() throws Exception {
        Application application = application(primaryUser, backendJob, ApplicationStatus.SAVED);
        Instant appliedAt = Instant.parse("2026-07-18T00:00:00Z");

        mockMvc.perform(patch("/api/v1/applications/" + application.getId())
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INTERVIEWING",
                                "appliedAt", appliedAt.toString(),
                                "notes", "Recruiter replied."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEWING"))
                .andExpect(jsonPath("$.notes").value("Recruiter replied."))
                .andExpect(jsonPath("$.appliedAt").value(appliedAt.toString()));

        mockMvc.perform(patch("/api/v1/applications/" + application.getId())
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearAppliedAt\":true,\"notes\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEWING"));

        Application updated = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(updated.getAppliedAt()).isNull();
        assertThat(updated.getNotes()).isNull();
    }

    @Test
    void listAndSummaryOnlyContainCurrentUsersApplicationsAndStatusFilterWorks() throws Exception {
        application(primaryUser, backendJob, ApplicationStatus.SAVED);
        application(primaryUser, frontendJob, ApplicationStatus.APPLIED);
        application(secondaryUser, backendJob, ApplicationStatus.OFFER);

        mockMvc.perform(get("/api/v1/applications")
                        .cookie(primaryCookie)
                        .param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/applications")
                        .cookie(primaryCookie)
                        .param("status", "APPLIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].jobId").value(frontendJob.getId().toString()));

        mockMvc.perform(get("/api/v1/applications/summary")
                        .cookie(primaryCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.byStatus.SAVED").value(1))
                .andExpect(jsonPath("$.byStatus.APPLIED").value(1))
                .andExpect(jsonPath("$.byStatus.OFFER").value(0));
    }

    @Test
    void ownerIsolationAppliesToReadUpdateAndDelete() throws Exception {
        Application application = application(primaryUser, backendJob, ApplicationStatus.SAVED);

        mockMvc.perform(get("/api/v1/applications/" + application.getId())
                        .cookie(primaryCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(application.getId().toString()))
                .andExpect(jsonPath("$.jobId").value(backendJob.getId().toString()));

        mockMvc.perform(get("/api/v1/applications/" + application.getId())
                        .cookie(secondaryCookie))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/applications/" + application.getId())
                        .cookie(secondaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/applications/" + application.getId())
                        .cookie(secondaryCookie)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(applicationRepository.findById(application.getId())).isPresent();
    }

    @Test
    void deleteRemovesOnlyCurrentUsersApplication() throws Exception {
        Application primaryApplication = application(primaryUser, backendJob, ApplicationStatus.SAVED);
        Application secondaryApplication = application(secondaryUser, frontendJob, ApplicationStatus.SAVED);

        mockMvc.perform(delete("/api/v1/applications/" + primaryApplication.getId())
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(applicationRepository.findById(primaryApplication.getId())).isEmpty();
        assertThat(applicationRepository.findById(secondaryApplication.getId())).isPresent();
    }

    @Test
    void inactiveJobCannotBeNewlyTracked() throws Exception {
        Job inactiveJob = job("Inactive Engineer", "OldCo", WorkMode.ON_SITE);
        inactiveJob.setActive(false);
        jobRepository.saveAndFlush(inactiveJob);

        mockMvc.perform(post("/api/v1/applications")
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("jobId", inactiveJob.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Cannot track an inactive job"));

        assertThat(applicationRepository.findByUserIdAndJobId(primaryUser.getId(), inactiveJob.getId())).isEmpty();
    }
    @Test
    void invalidJobEnumAndOversizedNotesReturnCleanBadRequests() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("jobId", UUID.randomUUID()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Job not found"));

        mockMvc.perform(post("/api/v1/applications")
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":\"" + backendJob.getId() + "\",\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid request body"));

        mockMvc.perform(post("/api/v1/applications")
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", backendJob.getId(),
                                "notes", "x".repeat(2001)
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Notes must be 2000 characters or fewer"));
    }

    @Test
    void unauthenticatedAccessIsRejectedAndUnsafeRequestsRequireCsrf() throws Exception {
        mockMvc.perform(get("/api/v1/applications"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/applications")
                        .cookie(primaryCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("jobId", backendJob.getId()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/applications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("jobId", backendJob.getId()))))
                .andExpect(status().isUnauthorized());
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName(email.substring(0, email.indexOf('@')));
        user.setLastName("User");
        user.setPasswordHash("hash");
        user.setRole(Role.USER);
        return userRepository.saveAndFlush(user);
    }

    private Job job(String title, String company, WorkMode workMode) {
        Job job = new Job();
        job.setTitle(title);
        job.setCompany(company);
        job.setLocation("Remote");
        job.setEmploymentType(EmploymentType.FULL_TIME);
        job.setWorkMode(workMode);
        job.setExternalUrl("https://example.com/jobs/" + title.toLowerCase().replace(" ", "-"));
        job.setSourceProvider("REMOTIVE");
        job.setActive(true);
        return jobRepository.saveAndFlush(job);
    }

    private Application application(User user, Job job, ApplicationStatus status) {
        Application application = new Application();
        application.setUser(user);
        application.setJob(job);
        application.setStatus(status);
        if (status == ApplicationStatus.APPLIED) {
            application.setAppliedAt(Instant.parse("2026-07-17T09:00:00Z"));
        }
        return applicationRepository.saveAndFlush(application);
    }

    private Cookie authCookie(User user) {
        return new Cookie("ARCLUME_ACCESS_TOKEN", jwtService.generateToken(
                user.getId().toString(),
                user.getEmail(),
                user.getRole().name()
        ));
    }
}

package com.arclume.api.security;

import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.Application;
import com.arclume.api.domain.ApplicationStatus;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.Hackathon;
import com.arclume.api.domain.HackathonMode;
import com.arclume.api.domain.HackathonOrganizerType;
import com.arclume.api.domain.Job;
import com.arclume.api.domain.JobRecommendation;
import com.arclume.api.domain.ParsingStatus;
import com.arclume.api.domain.RecommendationStatus;
import com.arclume.api.domain.Resume;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.StudentProgram;
import com.arclume.api.domain.StudentProgramMode;
import com.arclume.api.domain.StudentProgramType;
import com.arclume.api.domain.User;
import com.arclume.api.domain.WorkMode;
import com.arclume.api.repository.ApplicationRepository;
import com.arclume.api.repository.EmailVerificationTokenRepository;
import com.arclume.api.repository.HackathonRepository;
import com.arclume.api.repository.JobRecommendationRepository;
import com.arclume.api.repository.JobRepository;
import com.arclume.api.repository.RecoveryCodeRepository;
import com.arclume.api.repository.ResumeRepository;
import com.arclume.api.repository.StudentProgramRepository;
import com.arclume.api.repository.UserRepository;
import com.arclume.api.repository.UserSessionRepository;
import com.arclume.api.service.EmailVerificationService;
import com.arclume.api.service.RecoveryCodeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountDeletionIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "securePassword123";

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private UserRepository userRepository;
    @Autowired private UserSessionRepository userSessionRepository;
    @Autowired private EmailVerificationTokenRepository verificationTokenRepository;
    @Autowired private RecoveryCodeRepository recoveryCodeRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private JobRecommendationRepository recommendationRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private HackathonRepository hackathonRepository;
    @Autowired private StudentProgramRepository studentProgramRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SessionService sessionService;
    @Autowired private EmailVerificationService emailVerificationService;
    @Autowired private RecoveryCodeService recoveryCodeService;

    private UUID jobId;
    private UUID hackathonId;
    private UUID studentProgramId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        if (jobId != null) {
            jobRepository.deleteById(jobId);
        }
        if (hackathonId != null) {
            hackathonRepository.deleteById(hackathonId);
        }
        if (studentProgramId != null) {
            studentProgramRepository.deleteById(studentProgramId);
        }
    }

    @Test
    void anonymousUserCannotDeleteAccount() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/account").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson(PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordDoesNotDeleteAccountOrSession() throws Exception {
        User user = createUser("wrong-password@example.com");
        SessionService.IssuedSession session = sessionService.create(user, "integration-test", "127.0.0.1");
        Cookie sessionCookie = new Cookie(AuthCookieService.SESSION_COOKIE, session.token());

        mockMvc.perform(delete("/api/v1/auth/account").with(csrf())
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson("incorrect-password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"))
                .andExpect(jsonPath("$.message").value("Current password is incorrect."));

        assertThat(userRepository.findById(user.getId())).isPresent();
        assertThat(userSessionRepository.findAll()).hasSize(1);
        mockMvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk());
    }

    @Test
    void correctPasswordDeletesOwnedDataClearsCookiesAndKeepsSharedData() throws Exception {
        User user = createUser("delete-account@example.com");
        emailVerificationService.issueToken(user);
        recoveryCodeService.replaceCodes(user);
        SessionService.IssuedSession session = sessionService.create(user, "integration-test", "127.0.0.1");

        Job job = jobRepository.saveAndFlush(job());
        jobId = job.getId();
        Hackathon hackathon = hackathonRepository.saveAndFlush(hackathon());
        hackathonId = hackathon.getId();
        StudentProgram studentProgram = studentProgramRepository.saveAndFlush(studentProgram());
        studentProgramId = studentProgram.getId();

        Resume resume = new Resume();
        resume.setUser(user);
        resume.setFilename("account-delete-test.pdf");
        resume.setContentType("application/pdf");
        resume.setStorageRef("test/account-delete-test.pdf");
        resume.setParsingStatus(ParsingStatus.COMPLETED);
        resume = resumeRepository.saveAndFlush(resume);

        Application application = new Application();
        application.setUser(user);
        application.setJob(job);
        application.setStatus(ApplicationStatus.APPLIED);
        application.setAppliedAt(Instant.now());
        application = applicationRepository.saveAndFlush(application);

        JobRecommendation recommendation = new JobRecommendation();
        recommendation.setUser(user);
        recommendation.setJob(job);
        recommendation.setMatchScore(91);
        recommendation.setMatchedSkills(List.of("Java"));
        recommendation.setMissingSkills(List.of("Kubernetes"));
        recommendation.setExplanation("Strong match for account-deletion cascade testing.");
        recommendation.setStatus(RecommendationStatus.SAVED);
        recommendation.setGeneratedAt(Instant.now());
        recommendation = recommendationRepository.saveAndFlush(recommendation);

        Cookie sessionCookie = new Cookie(AuthCookieService.SESSION_COOKIE, session.token());
        Cookie challengeCookie = new Cookie(AuthCookieService.CHALLENGE_COOKIE, "pending-challenge");
        MvcResult result = mockMvc.perform(delete("/api/v1/auth/account").with(csrf())
                        .cookie(sessionCookie, challengeCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCOUNT_DELETED"))
                .andExpect(jsonPath("$.message").value("Your account has been permanently deleted."))
                .andReturn();

        assertCleared(result, AuthCookieService.SESSION_COOKIE);
        assertCleared(result, AuthCookieService.CHALLENGE_COOKIE);
        mockMvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "delete-account@example.com",
                                "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(verificationTokenRepository.findAll()).isEmpty();
        assertThat(recoveryCodeRepository.findAll()).isEmpty();
        assertThat(resumeRepository.findById(resume.getId())).isEmpty();
        assertThat(applicationRepository.findById(application.getId())).isEmpty();
        assertThat(recommendationRepository.findById(recommendation.getId())).isEmpty();

        assertThat(jobRepository.findById(jobId)).isPresent();
        assertThat(hackathonRepository.findById(hackathonId)).isPresent();
        assertThat(studentProgramRepository.findById(studentProgramId)).isPresent();
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Delete");
        user.setLastName("Test");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(Role.USER);
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    private Job job() {
        Job job = new Job();
        job.setTitle("Shared Backend Engineer");
        job.setCompany("Shared Opportunity Co");
        job.setLocation("Remote");
        job.setEmploymentType(EmploymentType.FULL_TIME);
        job.setWorkMode(WorkMode.REMOTE);
        job.setExternalUrl("https://example.test/jobs/" + UUID.randomUUID());
        job.setSourceProvider("TEST_SOURCE");
        job.setActive(true);
        return job;
    }

    private Hackathon hackathon() {
        Hackathon hackathon = new Hackathon();
        hackathon.setTitle("Shared Hackathon");
        hackathon.setOrganizer("Shared Organizer");
        hackathon.setOrganizerType(HackathonOrganizerType.COMMUNITY);
        hackathon.setMode(HackathonMode.ONLINE);
        hackathon.setExternalUrl("https://example.test/hackathons/" + UUID.randomUUID());
        hackathon.setSourceProvider("TEST_SOURCE");
        hackathon.setSourceId(UUID.randomUUID().toString());
        hackathon.setTags(List.of("shared"));
        hackathon.setActive(true);
        return hackathon;
    }

    private StudentProgram studentProgram() {
        StudentProgram program = new StudentProgram();
        program.setTitle("Shared Student Program");
        program.setCompany("Shared Program Co");
        program.setProgramType(StudentProgramType.LEARNING);
        program.setMode(StudentProgramMode.ONLINE);
        program.setAlwaysOpen(true);
        program.setExternalUrl("https://example.test/student-programs/" + UUID.randomUUID());
        program.setSourceProvider("TEST_SOURCE");
        program.setSourceId(UUID.randomUUID().toString());
        program.setBenefitTypes(List.of("TRAINING"));
        program.setTags(List.of("shared"));
        program.setActive(true);
        program.setLastVerifiedAt(LocalDate.of(2026, 7, 18));
        return program;
    }

    private String passwordJson(String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("password", password));
    }

    private void assertCleared(MvcResult result, String cookieName) {
        Cookie cookie = result.getResponse().getCookie(cookieName);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isZero();
    }
}

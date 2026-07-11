package com.arclume.api.recommendation;

import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.Job;
import com.arclume.api.domain.JobRecommendation;
import com.arclume.api.domain.ProficiencyLevel;
import com.arclume.api.domain.RecommendationStatus;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.Skill;
import com.arclume.api.domain.User;
import com.arclume.api.domain.UserSkill;
import com.arclume.api.domain.WorkMode;
import com.arclume.api.dto.RecommendationStatusRequest;
import com.arclume.api.repository.ApplicationRepository;
import com.arclume.api.repository.JobRecommendationRepository;
import com.arclume.api.repository.JobRepository;
import com.arclume.api.repository.ResumeRepository;
import com.arclume.api.repository.SkillRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.ai.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class JobRecommendationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private JobRecommendationRepository recommendationRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User primaryUser;
    private User secondaryUser;
    private Cookie primaryCookie;
    private Cookie secondaryCookie;
    private Job javaReactJob;
    private Job postgresJob;
    private Job inactiveJob;
    private Skill react;

    @BeforeEach
    void setUp() {
        recommendationRepository.deleteAll();
        applicationRepository.deleteAll();
        userSkillRepository.deleteAll();
        resumeRepository.deleteAll();
        skillRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();

        primaryUser = user("alice@example.com");
        secondaryUser = user("bob@example.com");
        primaryCookie = authCookie(primaryUser);
        secondaryCookie = authCookie(secondaryUser);

        Skill java = skill("Java");
        react = skill("React");
        Skill postgres = skill("Postgres");
        userSkillRepository.saveAndFlush(new UserSkill(primaryUser, java, ProficiencyLevel.ADVANCED));
        userSkillRepository.saveAndFlush(new UserSkill(primaryUser, postgres, ProficiencyLevel.INTERMEDIATE));

        javaReactJob = job("Backend Engineer", "Arclume", "Remote", EmploymentType.FULL_TIME, WorkMode.REMOTE,
                "Build services with Java and React.", "Java, React", true);
        postgresJob = job("Data Engineer", "Lumina", "New York", EmploymentType.CONTRACT, WorkMode.HYBRID,
                "Own Postgres systems.", "Postgres", true);
        inactiveJob = job("Legacy Engineer", "OldCo", "Boston", EmploymentType.PART_TIME, WorkMode.ON_SITE,
                "Maintain Java systems.", "Java", false);
    }

    @Test
    void refreshCreatesRecommendationsWithPersistedSkillsScoreAndStatus() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/refresh")
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.expired").value(0))
                .andExpect(jsonPath("$.failed").value(0));

        List<JobRecommendation> recommendations = recommendationRepository.findAll();
        assertThat(recommendations).hasSize(2);

        JobRecommendation javaReactRecommendation = recommendationRepository
                .findByUserIdAndJobId(primaryUser.getId(), javaReactJob.getId())
                .orElseThrow();
        assertThat(javaReactRecommendation.getMatchedSkills()).containsExactly("Java");
        assertThat(javaReactRecommendation.getMissingSkills()).containsExactly(react.getName());
        assertThat(javaReactRecommendation.getMatchScore()).isEqualTo(50);
        assertThat(javaReactRecommendation.getStatus()).isEqualTo(RecommendationStatus.ACTIVE);
        assertThat(javaReactRecommendation.getExplanation()).contains("Moderate match");
    }

    @Test
    void refreshIsIdempotentAndUpdatesExistingRecommendationInsteadOfDuplicating() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/refresh")
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2));

        mockMvc.perform(post("/api/v1/recommendations/refresh")
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(2));

        assertThat(recommendationRepository.findAll()).hasSize(2);
        assertThat(recommendationRepository.countByUserIdAndStatus(primaryUser.getId(), RecommendationStatus.ACTIVE)).isEqualTo(2);
    }

    @Test
    void usersCanOnlyReadAndUpdateTheirOwnRecommendations() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/refresh")
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isOk());
        JobRecommendation recommendation = recommendationRepository
                .findByUserIdAndJobId(primaryUser.getId(), javaReactJob.getId())
                .orElseThrow();

        mockMvc.perform(get("/api/v1/recommendations/" + recommendation.getId())
                        .cookie(secondaryCookie))
                .andExpect(status().isForbidden());

        RecommendationStatusRequest request = new RecommendationStatusRequest();
        request.setStatus(RecommendationStatus.SAVED);
        mockMvc.perform(patch("/api/v1/recommendations/" + recommendation.getId() + "/status")
                        .cookie(secondaryCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusChangesRequireAuthenticationAndCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/refresh")
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isOk());
        JobRecommendation recommendation = recommendationRepository
                .findByUserIdAndJobId(primaryUser.getId(), javaReactJob.getId())
                .orElseThrow();
        RecommendationStatusRequest request = new RecommendationStatusRequest();
        request.setStatus(RecommendationStatus.DISMISSED);

        mockMvc.perform(patch("/api/v1/recommendations/" + recommendation.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/recommendations/" + recommendation.getId() + "/status")
                        .cookie(primaryCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/recommendations/" + recommendation.getId() + "/status")
                        .cookie(primaryCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));
    }

    @Test
    void listSupportsPaginationSortingAndFilters() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/refresh")
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/recommendations")
                        .cookie(primaryCookie)
                        .param("page", "0")
                        .param("size", "1")
                        .param("sortBy", "matchScore")
                        .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].jobTitle").value("Data Engineer"))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/recommendations")
                        .cookie(primaryCookie)
                        .param("minimumScore", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].matchScore").value(100));

        mockMvc.perform(get("/api/v1/recommendations")
                        .cookie(primaryCookie)
                        .param("workMode", "HYBRID")
                        .param("employmentType", "CONTRACT")
                        .param("company", "lumi")
                        .param("location", "york"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("Lumina"));
    }

    @Test
    void refreshExpiresInactiveJobsAndDeletedJobsAreRemovedByCascade() throws Exception {
        JobRecommendation stale = new JobRecommendation();
        stale.setUser(primaryUser);
        stale.setJob(jobRepository.findById(inactiveJob.getId()).orElseThrow());
        stale.setMatchScore(100);
        stale.setMatchedSkills(List.of("Java"));
        stale.setMissingSkills(List.of());
        stale.setExplanation("Previously active recommendation.");
        stale.setStatus(RecommendationStatus.ACTIVE);
        stale.setGeneratedAt(java.time.Instant.now());
        stale = recommendationRepository.saveAndFlush(stale);

        mockMvc.perform(post("/api/v1/recommendations/refresh")
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.expired").value(1));

        assertThat(recommendationRepository.findById(stale.getId()).orElseThrow().getStatus())
                .isEqualTo(RecommendationStatus.EXPIRED);

        JobRecommendation postgresRecommendation = recommendationRepository
                .findByUserIdAndJobId(primaryUser.getId(), postgresJob.getId())
                .orElseThrow();
        entityManager.clear();
        jobRepository.deleteById(postgresJob.getId());
        jobRepository.flush();

        assertThat(recommendationRepository.findById(postgresRecommendation.getId())).isEmpty();
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

    private Skill skill(String name) {
        Skill skill = new Skill();
        skill.setName(name);
        return skillRepository.saveAndFlush(skill);
    }

    private Job job(
            String title,
            String company,
            String location,
            EmploymentType employmentType,
            WorkMode workMode,
            String description,
            String requirements,
            boolean active) {
        Job job = new Job();
        job.setTitle(title);
        job.setCompany(company);
        job.setLocation(location);
        job.setEmploymentType(employmentType);
        job.setWorkMode(workMode);
        job.setDescription(description);
        job.setRequirements(requirements);
        job.setActive(active);
        return jobRepository.saveAndFlush(job);
    }

    private Cookie authCookie(User user) {
        return new Cookie("ARCLUME_ACCESS_TOKEN", jwtService.generateToken(
                user.getId().toString(),
                user.getEmail(),
                user.getRole().name()
        ));
    }
}




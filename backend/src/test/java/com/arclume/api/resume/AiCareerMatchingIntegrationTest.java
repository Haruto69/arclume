package com.arclume.api.resume;

import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.Job;
import com.arclume.api.domain.ParsingStatus;
import com.arclume.api.domain.ProficiencyLevel;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.Resume;
import com.arclume.api.domain.Skill;
import com.arclume.api.domain.User;
import com.arclume.api.domain.UserSkill;
import com.arclume.api.dto.AiProcessRequest;
import com.arclume.api.repository.JobRepository;
import com.arclume.api.repository.ResumeRepository;
import com.arclume.api.repository.SkillRepository;
import com.arclume.api.repository.UserRepository;
import com.arclume.api.repository.UserSkillRepository;
import com.arclume.api.security.JwtService;
import com.arclume.api.service.ResumeService;
import com.arclume.api.service.ai.AiProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.Cookie;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = {
    "app.ai.enabled=true",
    "app.ai.api-key=test-api-key"
})
@AutoConfigureMockMvc
@Transactional
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
class AiCareerMatchingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private com.arclume.api.repository.ApplicationRepository applicationRepository;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private com.arclume.api.service.ai.AiResumeService aiResumeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AiProvider mockAiProvider;

    private User primaryUser;
    private User secondaryUser;
    private Cookie primaryCookie;
    private Cookie secondaryCookie;
    private Resume testResume;

    @BeforeEach
    void setUp() throws Exception {
        applicationRepository.deleteAll();
        userSkillRepository.deleteAll();
        resumeRepository.deleteAll();
        skillRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();

        // Users & Authentication
        primaryUser = new User();
        primaryUser.setEmail("alice@example.com");
        primaryUser.setFirstName("Alice");
        primaryUser.setLastName("Green");
        primaryUser.setPasswordHash("hash");
        primaryUser.setRole(Role.USER);
        primaryUser = userRepository.saveAndFlush(primaryUser);
        primaryCookie = new Cookie("ARCLUME_ACCESS_TOKEN", jwtService.generateToken(primaryUser.getId().toString(), primaryUser.getEmail(), primaryUser.getRole().name()));

        secondaryUser = new User();
        secondaryUser.setEmail("bob@example.com");
        secondaryUser.setFirstName("Bob");
        secondaryUser.setLastName("Blue");
        secondaryUser.setPasswordHash("hash");
        secondaryUser.setRole(Role.USER);
        secondaryUser = userRepository.saveAndFlush(secondaryUser);
        secondaryCookie = new Cookie("ARCLUME_ACCESS_TOKEN", jwtService.generateToken(secondaryUser.getId().toString(), secondaryUser.getEmail(), secondaryUser.getRole().name()));

        // Upload a base resume
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.txt",
                "text/plain",
                "I am a Java developer with expertise in Docker.".getBytes()
        );
        testResume = resumeService.uploadResume(file, primaryUser);
    }

    @Test
    void processResumeWithAiRequiresConsent() throws Exception {
        AiProcessRequest request = new AiProcessRequest();
        request.setConsent(false);

        mockMvc.perform(post("/api/v1/resumes/" + testResume.getId() + "/ai-process")
                        .cookie(primaryCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processResumeWithAiEnforcesOwnerIsolation() throws Exception {
        AiProcessRequest request = new AiProcessRequest();
        request.setConsent(true);

        mockMvc.perform(post("/api/v1/resumes/" + testResume.getId() + "/ai-process")
                        .cookie(secondaryCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void processResumeWithAiSuccess() throws Exception {
        Skill s1 = new Skill();
        s1.setName("Java");
        skillRepository.saveAndFlush(s1);

        Skill s2 = new Skill();
        s2.setName("Docker");
        skillRepository.saveAndFlush(s2);

        // Prepare Mock AI JSON response matching the expected schema
        String mockJsonResponse = "{\n" +
                "  \"skills\": [\n" +
                "    {\"name\": \"Java\", \"proficiency\": \"ADVANCED\", \"confidence\": 0.95},\n" +
                "    {\"name\": \"Docker\", \"proficiency\": \"INTERMEDIATE\", \"confidence\": 0.85}\n" +
                "  ],\n" +
                "  \"categories\": [\"Software Engineer\"],\n" +
                "  \"yearsOfExperience\": 4\n" +
                "}";

        when(mockAiProvider.generateChatCompletion(anyString(), anyString())).thenReturn(mockJsonResponse);

        AiProcessRequest request = new AiProcessRequest();
        request.setConsent(true);

        mockMvc.perform(post("/api/v1/resumes/" + testResume.getId() + "/ai-process")
                        .cookie(primaryCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk());

        entityManager.clear();

        Resume processed = resumeRepository.findById(testResume.getId()).orElseThrow();
        assertThat(processed.getParsingStatus()).isEqualTo(ParsingStatus.COMPLETED);

        List<UserSkill> skills = userSkillRepository.findByUserId(primaryUser.getId());
        assertThat(skills).hasSize(2);
        assertThat(skills.stream().map(us -> us.getSkill().getName())).containsExactlyInAnyOrder("Java", "Docker");
        assertThat(skills.stream().filter(us -> us.getSkill().getName().equals("Java")).findFirst().get().getProficiencyLevel())
                .isEqualTo(ProficiencyLevel.ADVANCED);
    }

    @Test
    void processResumeWithAiFallbackOnProviderTimeout() throws Exception {
        Skill s1 = new Skill();
        s1.setName("Java");
        skillRepository.saveAndFlush(s1);

        // Make AI provider throw timeout/network exception
        when(mockAiProvider.generateChatCompletion(anyString(), anyString())).thenThrow(new RuntimeException("Timeout connecting to provider"));

        AiProcessRequest request = new AiProcessRequest();
        request.setConsent(true);

        // The endpoint should complete with 200 OK because it falls back to deterministic extraction
        mockMvc.perform(post("/api/v1/resumes/" + testResume.getId() + "/ai-process")
                        .cookie(primaryCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk());

        Resume processed = resumeRepository.findById(testResume.getId()).orElseThrow();
        // Since deterministic parsing completed, status will be COMPLETED
        assertThat(processed.getParsingStatus()).isEqualTo(ParsingStatus.COMPLETED);

        // It should match "Java" deterministically
        List<UserSkill> skills = userSkillRepository.findByUserId(primaryUser.getId());
        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).getSkill().getName()).isEqualTo("Java");
    }

    @Test
    void processResumeWithAiFallbackOnMalformedJson() throws Exception {
        Skill s1 = new Skill();
        s1.setName("Java");
        skillRepository.saveAndFlush(s1);

        // AI returns malformed JSON
        when(mockAiProvider.generateChatCompletion(anyString(), anyString())).thenReturn("This is not JSON");

        AiProcessRequest request = new AiProcessRequest();
        request.setConsent(true);

        mockMvc.perform(post("/api/v1/resumes/" + testResume.getId() + "/ai-process")
                        .cookie(primaryCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk());

        Resume processed = resumeRepository.findById(testResume.getId()).orElseThrow();
        assertThat(processed.getParsingStatus()).isEqualTo(ParsingStatus.COMPLETED);

        List<UserSkill> skills = userSkillRepository.findByUserId(primaryUser.getId());
        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).getSkill().getName()).isEqualTo("Java");
    }

    @Test
    void careerMatchingWorksCorrectly() throws Exception {
        // Setup Skills
        Skill s1 = new Skill();
        s1.setName("Java");
        skillRepository.saveAndFlush(s1);

        Skill s2 = new Skill();
        s2.setName("Postgres");
        skillRepository.saveAndFlush(s2);

        Skill s3 = new Skill();
        s3.setName("React");
        skillRepository.saveAndFlush(s3);

        // User has Java & Postgres
        UserSkill us1 = new UserSkill(primaryUser, s1, ProficiencyLevel.ADVANCED);
        userSkillRepository.saveAndFlush(us1);
        UserSkill us2 = new UserSkill(primaryUser, s2, ProficiencyLevel.INTERMEDIATE);
        userSkillRepository.saveAndFlush(us2);

        // Job requires Java & React
        Job job = new Job();
        job.setTitle("Software Engineer");
        job.setCompany("Arclume Corp");
        job.setDescription("We are looking for a Java engineer with React knowledge.");
        job.setRequirements("Required skills: Java, React");
        job = jobRepository.saveAndFlush(job);

        mockMvc.perform(post("/api/v1/jobs/" + job.getId() + "/match")
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Software Engineer"))
                .andExpect(jsonPath("$.companyName").value("Arclume Corp"))
                .andExpect(jsonPath("$.matchedSkills[0]").value("Java"))
                .andExpect(jsonPath("$.missingSkills[0]").value("React"))
                .andExpect(jsonPath("$.matchScore").value(50)) // 1 matched out of 2 required
                .andExpect(jsonPath("$.explanation").value(org.hamcrest.Matchers.containsString("Moderate match")));
     }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        org.springframework.test.util.ReflectionTestUtils.setField(aiResumeService, "aiEnabled", true);
    }

    @Test
    void processResumeWithAiDisabledFallbackToDeterministic() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(aiResumeService, "aiEnabled", false);

        Skill s1 = new Skill();
        s1.setName("Java");
        skillRepository.saveAndFlush(s1);

        AiProcessRequest request = new AiProcessRequest();
        request.setConsent(true);

        mockMvc.perform(post("/api/v1/resumes/" + testResume.getId() + "/ai-process")
                        .cookie(primaryCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk());

        entityManager.clear();

        Resume processed = resumeRepository.findById(testResume.getId()).orElseThrow();
        assertThat(processed.getParsingStatus()).isEqualTo(ParsingStatus.COMPLETED);

        List<UserSkill> skills = userSkillRepository.findByUserId(primaryUser.getId());
        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).getSkill().getName()).isEqualTo("Java");
    }

    @Test
    void processResumeWithAiMissingApiKeyFallbackToDeterministic() throws Exception {
        // Stub AI provider to throw an exception representing missing API key
        when(mockAiProvider.generateChatCompletion(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("AI provider API key is missing"));

        Skill s1 = new Skill();
        s1.setName("Java");
        skillRepository.saveAndFlush(s1);

        AiProcessRequest request = new AiProcessRequest();
        request.setConsent(true);

        mockMvc.perform(post("/api/v1/resumes/" + testResume.getId() + "/ai-process")
                        .cookie(primaryCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk());

        entityManager.clear();

        Resume processed = resumeRepository.findById(testResume.getId()).orElseThrow();
        assertThat(processed.getParsingStatus()).isEqualTo(ParsingStatus.COMPLETED);

        List<UserSkill> skills = userSkillRepository.findByUserId(primaryUser.getId());
        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).getSkill().getName()).isEqualTo("Java");
    }
}

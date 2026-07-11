package com.arclume.api.resume;

import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.ParsingStatus;
import com.arclume.api.domain.ProficiencyLevel;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.Resume;
import com.arclume.api.domain.Skill;
import com.arclume.api.domain.User;
import com.arclume.api.domain.UserSkill;
import com.arclume.api.repository.ResumeRepository;
import com.arclume.api.repository.SkillRepository;
import com.arclume.api.repository.UserRepository;
import com.arclume.api.repository.UserSkillRepository;
import com.arclume.api.security.JwtService;
import com.arclume.api.service.ResumeService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ResumeProcessingIntegrationTest extends BaseIntegrationTest {

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
    private ResumeService resumeService;

    @Autowired
    private JwtService jwtService;

    private User primaryUser;
    private User secondaryUser;
    private Cookie primaryCookie;
    private Cookie secondaryCookie;

    @BeforeEach
    void setUp() {
        userSkillRepository.deleteAll();
        resumeRepository.deleteAll();
        skillRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Create primary user
        primaryUser = new User();
        primaryUser.setEmail("primary@example.com");
        primaryUser.setFirstName("Alice");
        primaryUser.setLastName("Green");
        primaryUser.setPasswordHash("hash");
        primaryUser.setRole(Role.USER);
        primaryUser = userRepository.saveAndFlush(primaryUser);
        String token1 = jwtService.generateToken(primaryUser.getId().toString(), primaryUser.getEmail(), primaryUser.getRole().name());
        primaryCookie = new Cookie("ARCLUME_ACCESS_TOKEN", token1);

        // 2. Create secondary user
        secondaryUser = new User();
        secondaryUser.setEmail("secondary@example.com");
        secondaryUser.setFirstName("Bob");
        secondaryUser.setLastName("Blue");
        secondaryUser.setPasswordHash("hash");
        secondaryUser.setRole(Role.USER);
        secondaryUser = userRepository.saveAndFlush(secondaryUser);
        String token2 = jwtService.generateToken(secondaryUser.getId().toString(), secondaryUser.getEmail(), secondaryUser.getRole().name());
        secondaryCookie = new Cookie("ARCLUME_ACCESS_TOKEN", token2);
    }

    private byte[] createDocxBytes(String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r = p.createRun();
            r.setText(text);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    @Test
    void uploadAndProcessValidTxtResume() throws Exception {
        // Setup Skills in database to match against
        Skill s1 = new Skill();
        s1.setName("Java");
        skillRepository.saveAndFlush(s1);

        Skill s2 = new Skill();
        s2.setName("Spring");
        skillRepository.saveAndFlush(s2);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.txt",
                "text/plain",
                "I am a Java developer with expertise in Spring Framework.".getBytes()
        );

        // 1. Upload
        String json = mockMvc.perform(multipart("/api/v1/resumes")
                        .file(file)
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String resumeIdStr = com.jayway.jsonpath.JsonPath.read(json, "$.id");
        UUID resumeId = UUID.fromString(resumeIdStr);

        // 2. Process
        mockMvc.perform(post("/api/v1/resumes/" + resumeId + "/process")
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isOk());

        Resume processed = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(processed.getParsingStatus()).isEqualTo(ParsingStatus.COMPLETED);
        assertThat(processed.getExtractedText()).contains("Java developer");

        // Verify Skills matched and saved
        List<UserSkill> skills = userSkillRepository.findByUserId(primaryUser.getId());
        assertThat(skills).hasSize(2);
        assertThat(skills.stream().map(us -> us.getSkill().getName())).containsExactlyInAnyOrder("Java", "Spring");
    }

    @Test
    void uploadAndProcessValidDocxResume() throws Exception {
        Skill s = new Skill();
        s.setName("Postgres");
        skillRepository.saveAndFlush(s);

        byte[] docxBytes = createDocxBytes("Extensive database experience using Postgres.");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes
        );

        String json = mockMvc.perform(multipart("/api/v1/resumes")
                        .file(file)
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String resumeIdStr = com.jayway.jsonpath.JsonPath.read(json, "$.id");
        UUID resumeId = UUID.fromString(resumeIdStr);

        mockMvc.perform(post("/api/v1/resumes/" + resumeId + "/process")
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isOk());

        Resume processed = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(processed.getParsingStatus()).isEqualTo(ParsingStatus.COMPLETED);
        assertThat(processed.getExtractedText()).contains("database experience using Postgres");

        List<UserSkill> skills = userSkillRepository.findByUserId(primaryUser.getId());
        assertThat(skills).hasSize(1);
    }

    @Test
    void rejectUnsupportedFileType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.sh",
                "application/x-sh",
                "echo 'unsafe'".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/resumes")
                        .file(file)
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectOversizedFile() throws Exception {
        // Create 6MB file (larger than 5MB limit)
        byte[] oversizedData = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.txt",
                "text/plain",
                oversizedData
        );

        mockMvc.perform(multipart("/api/v1/resumes")
                        .file(file)
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectUnsafeFilenamesPathTraversal() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../unsafe.txt",
                "text/plain",
                "safe content".getBytes()
        );

        String json = mockMvc.perform(multipart("/api/v1/resumes")
                        .file(file)
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String filenameStr = com.jayway.jsonpath.JsonPath.read(json, "$.filename");
        // Confirm path traversal patterns were replaced/removed
        assertThat(filenameStr).doesNotContain("..");
    }

    @Test
    void enforceUserIsolation() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.txt",
                "text/plain",
                "Primary User Resume".getBytes()
        );

        Resume resume = resumeService.uploadResume(file, primaryUser);

        // Secondary user tries to fetch primary user's resume
        mockMvc.perform(get("/api/v1/resumes/" + resume.getId())
                        .cookie(secondaryCookie))
                .andExpect(status().isForbidden());

        // Secondary user tries to process primary user's resume
        mockMvc.perform(post("/api/v1/resumes/" + resume.getId() + "/process")
                        .cookie(secondaryCookie)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        // Secondary user tries to delete primary user's resume
        mockMvc.perform(delete("/api/v1/resumes/" + resume.getId())
                        .cookie(secondaryCookie)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteCleansUpLocalPhysicalFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "temp.txt",
                "text/plain",
                "Temporary".getBytes()
        );

        Resume resume = resumeService.uploadResume(file, primaryUser);
        File localFile = new File(resume.getStorageRef());
        assertThat(localFile.exists()).isTrue();

        mockMvc.perform(delete("/api/v1/resumes/" + resume.getId())
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(localFile.exists()).isFalse();
        assertThat(resumeRepository.findById(resume.getId())).isEmpty();
    }

    @Test
    void parseFailureStatusHandling() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bad.pdf",
                "application/pdf",
                "Not a valid PDF format".getBytes()
        );

        Resume resume = resumeService.uploadResume(file, primaryUser);

        mockMvc.perform(post("/api/v1/resumes/" + resume.getId() + "/process")
                        .cookie(primaryCookie)
                        .with(csrf()))
                .andExpect(status().isInternalServerError());

        Resume processed = resumeRepository.findById(resume.getId()).orElseThrow();
        assertThat(processed.getParsingStatus()).isEqualTo(ParsingStatus.FAILED);
    }
}

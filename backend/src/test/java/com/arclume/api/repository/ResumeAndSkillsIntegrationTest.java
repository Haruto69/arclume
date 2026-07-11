package com.arclume.api.repository;

import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.ParsingStatus;
import com.arclume.api.domain.ProficiencyLevel;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.Resume;
import com.arclume.api.domain.Skill;
import com.arclume.api.domain.User;
import com.arclume.api.domain.UserSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ResumeAndSkillsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private User testUser;

    @BeforeEach
    void setUp() {
        userSkillRepository.deleteAll();
        resumeRepository.deleteAll();
        skillRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setEmail("johndoe@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPasswordHash("$2a$10$DxJ72vqN3VDbH.oX1.Y6Q.xZ1tO7YtQp2U4L7M5g1oWz0eK9yvN2m");
        user.setRole(Role.USER);
        testUser = userRepository.saveAndFlush(user);
    }

    @Test
    void whenResumeIsSaved_thenItCanBeRetrievedAndAudited() {
        Resume resume = new Resume();
        resume.setUser(testUser);
        resume.setFilename("resume.pdf");
        resume.setContentType("application/pdf");
        resume.setStorageRef("/storage/resumes/resume.pdf");
        resume.setParsingStatus(ParsingStatus.PENDING);
        resume.setExtractedText("John Doe Software Engineer Profile...");

        Resume savedResume = resumeRepository.saveAndFlush(resume);

        assertThat(savedResume.getId()).isNotNull();
        assertThat(savedResume.getCreatedAt()).isNotNull();
        assertThat(savedResume.getUpdatedAt()).isNotNull();

        Optional<Resume> retrieved = resumeRepository.findById(savedResume.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getFilename()).isEqualTo("resume.pdf");
        assertThat(retrieved.get().getParsingStatus()).isEqualTo(ParsingStatus.PENDING);
    }

    @Test
    void whenDuplicateSkillIsSaved_thenThrowException() {
        Skill s1 = new Skill();
        s1.setName("Java");
        skillRepository.saveAndFlush(s1);

        Skill s2 = new Skill();
        s2.setName("Java"); // Case sensitive or exact name match

        assertThatThrownBy(() -> skillRepository.saveAndFlush(s2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void whenUserSkillIsSaved_thenItCanBeRetrievedAndAudited() {
        Skill skill = new Skill();
        skill.setName("Java");
        skill = skillRepository.saveAndFlush(skill);

        UserSkill userSkill = new UserSkill(testUser, skill, ProficiencyLevel.EXPERT);
        UserSkill saved = userSkillRepository.saveAndFlush(userSkill);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        List<UserSkill> retrieved = userSkillRepository.findByUserId(testUser.getId());
        assertThat(retrieved).hasSize(1);
        assertThat(retrieved.get(0).getProficiencyLevel()).isEqualTo(ProficiencyLevel.EXPERT);
        assertThat(retrieved.get(0).getSkill().getName()).isEqualTo("Java");
    }

    @Test
    void whenUserIsDeleted_thenResumesAndSkillsAreCascaded() {
        Skill skill = new Skill();
        skill.setName("Spring Boot");
        skill = skillRepository.saveAndFlush(skill);

        UserSkill userSkill = new UserSkill(testUser, skill, ProficiencyLevel.INTERMEDIATE);
        testUser.getUserSkills().add(userSkill);
        userSkillRepository.saveAndFlush(userSkill);

        Resume resume = new Resume();
        resume.setUser(testUser);
        resume.setFilename("resume.pdf");
        resume.setContentType("application/pdf");
        resume.setStorageRef("/storage/resumes/resume.pdf");
        resume.setParsingStatus(ParsingStatus.PENDING);
        testUser.getResumes().add(resume);
        resumeRepository.saveAndFlush(resume);

        assertThat(resumeRepository.findByUserId(testUser.getId())).hasSize(1);
        assertThat(userSkillRepository.findByUserId(testUser.getId())).hasSize(1);

        // Delete user via custom query to bypass Hibernate object graph checks
        userRepository.deleteUserById(testUser.getId());
        userRepository.flush();
        entityManager.clear(); // Clear persistence context to force reload from DB

        // Associations should be deleted automatically via ON DELETE CASCADE
        assertThat(resumeRepository.findByUserId(testUser.getId())).isEmpty();
        assertThat(userSkillRepository.findByUserId(testUser.getId())).isEmpty();
        
        // Skill itself remains
        assertThat(skillRepository.findById(skill.getId())).isPresent();
    }
}

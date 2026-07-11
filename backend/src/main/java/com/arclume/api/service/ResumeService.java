package com.arclume.api.service;

import com.arclume.api.domain.ParsingStatus;
import com.arclume.api.domain.ProficiencyLevel;
import com.arclume.api.domain.Resume;
import com.arclume.api.domain.Skill;
import com.arclume.api.domain.User;
import com.arclume.api.domain.UserSkill;
import com.arclume.api.repository.ResumeRepository;
import com.arclume.api.repository.SkillRepository;
import com.arclume.api.repository.UserSkillRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final SkillRepository skillRepository;
    private final UserSkillRepository userSkillRepository;
    private final ResumeParser resumeParser;
    private final Path storageDirectory;
    private final long maxSizeBytes;

    public ResumeService(
            ResumeRepository resumeRepository,
            SkillRepository skillRepository,
            UserSkillRepository userSkillRepository,
            ResumeParser resumeParser,
            @Value("${app.storage.resumes-dir:storage/resumes}") String storageDir,
            @Value("${app.resumes.max-size-bytes:5242880}") long maxSizeBytes) throws IOException {
        this.resumeRepository = resumeRepository;
        this.skillRepository = skillRepository;
        this.userSkillRepository = userSkillRepository;
        this.resumeParser = resumeParser;
        this.storageDirectory = Paths.get(storageDir).toAbsolutePath().normalize();
        this.maxSizeBytes = maxSizeBytes;
        Files.createDirectories(this.storageDirectory);
    }

    @Transactional
    public Resume uploadResume(MultipartFile file, User user) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload empty file");
        }

        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException("File size exceeds maximum limit of " + maxSizeBytes + " bytes");
        }

        String contentType = file.getContentType();
        if (contentType == null || !isSupportedMimeType(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("Original filename cannot be null");
        }

        String sanitizedFilename = sanitizeFilename(originalFilename);
        if (!hasSupportedExtension(sanitizedFilename)) {
            throw new IllegalArgumentException("Unsupported file extension");
        }

        String uniqueFilename = UUID.randomUUID().toString() + "_" + sanitizedFilename;
        Path targetPath = this.storageDirectory.resolve(uniqueFilename).normalize();

        // Path traversal defense
        if (!targetPath.startsWith(this.storageDirectory)) {
            throw new IllegalArgumentException("Invalid file upload path");
        }

        Files.copy(file.getInputStream(), targetPath);

        Resume resume = new Resume();
        resume.setUser(user);
        resume.setFilename(sanitizedFilename);
        resume.setContentType(contentType);
        resume.setStorageRef(targetPath.toString());
        resume.setParsingStatus(ParsingStatus.PENDING);

        return resumeRepository.save(resume);
    }

    @Transactional
    public void processResume(UUID resumeId, User currentUser) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found"));

        validateOwner(resume, currentUser);

        resume.setParsingStatus(ParsingStatus.PROCESSING);
        resumeRepository.saveAndFlush(resume);

        File file = new File(resume.getStorageRef());
        try (FileInputStream fis = new FileInputStream(file)) {
            String extractedText = resumeParser.extractText(fis, resume.getContentType());
            resume.setExtractedText(extractedText);
            resume.setParsingStatus(ParsingStatus.COMPLETED);
            resumeRepository.save(resume);

            // Extract skills from text
            extractAndSaveSkills(extractedText, currentUser);

        } catch (Exception e) {
            resume.setParsingStatus(ParsingStatus.FAILED);
            resumeRepository.save(resume);
            throw new RuntimeException("Deterministic parsing failed: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void deleteResume(UUID resumeId, User currentUser) throws IOException {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found"));

        validateOwner(resume, currentUser);

        // Delete physical file
        Path filePath = Paths.get(resume.getStorageRef());
        Files.deleteIfExists(filePath);

        resumeRepository.delete(resume);
    }

    public List<Resume> getUserResumes(User currentUser) {
        return resumeRepository.findByUserId(currentUser.getId());
    }

    public Resume getResume(UUID resumeId, User currentUser) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found"));
        validateOwner(resume, currentUser);
        return resume;
    }

    private void extractAndSaveSkills(String text, User user) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        List<Skill> allSkills = skillRepository.findAll();
        List<UserSkill> existingUserSkills = userSkillRepository.findByUserId(user.getId());

        for (Skill skill : allSkills) {
            String skillName = skill.getName().toLowerCase();
            // Match boundaries to avoid substring matching (e.g. match "Java" but not "Javascript")
            String patternString = "\\b" + Pattern.quote(skillName) + "\\b";
            Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);

            if (pattern.matcher(text).find()) {
                boolean alreadyHasSkill = existingUserSkills.stream()
                        .anyMatch(us -> us.getSkill().getId().equals(skill.getId()));

                if (!alreadyHasSkill) {
                    UserSkill userSkill = new UserSkill(user, skill, ProficiencyLevel.BEGINNER);
                    userSkillRepository.save(userSkill);
                }
            }
        }
    }

    public void validateOwner(Resume resume, User currentUser) {
        if (!resume.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Access denied to this resume resources");
        }
    }

    private boolean isSupportedMimeType(String mimeType) {
        return "application/pdf".equals(mimeType) ||
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mimeType) ||
                "text/plain".equals(mimeType);
    }

    private boolean hasSupportedExtension(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".txt");
    }

    private String sanitizeFilename(String filename) {
        // Strip out paths
        String nameOnly = Paths.get(filename).getFileName().toString();
        // Replace everything except alphanumeric, dots, hyphens, and underscores
        return nameOnly.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
    }
}

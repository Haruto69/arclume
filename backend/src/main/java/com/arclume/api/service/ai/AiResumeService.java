package com.arclume.api.service.ai;

import com.arclume.api.domain.ParsingStatus;
import com.arclume.api.domain.ProficiencyLevel;
import com.arclume.api.domain.Resume;
import com.arclume.api.domain.Skill;
import com.arclume.api.domain.User;
import com.arclume.api.domain.UserSkill;
import com.arclume.api.repository.ResumeRepository;
import com.arclume.api.repository.SkillRepository;
import com.arclume.api.repository.UserSkillRepository;
import com.arclume.api.service.ResumeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AiResumeService {

    private final AiProvider aiProvider;
    private final ResumeRepository resumeRepository;
    private final SkillRepository skillRepository;
    private final UserSkillRepository userSkillRepository;
    private final ResumeService resumeService;
    private final ObjectMapper objectMapper;
    private final int maxInputChars;
    private final boolean aiEnabled;

    public AiResumeService(
            AiProvider aiProvider,
            ResumeRepository resumeRepository,
            SkillRepository skillRepository,
            UserSkillRepository userSkillRepository,
            ResumeService resumeService,
            @Value("${app.ai.max-input-chars:8000}") int maxInputChars,
            @Value("${app.ai.enabled:false}") boolean aiEnabled) {
        this.aiProvider = aiProvider;
        this.resumeRepository = resumeRepository;
        this.skillRepository = skillRepository;
        this.userSkillRepository = userSkillRepository;
        this.resumeService = resumeService;
        this.objectMapper = new ObjectMapper();
        this.maxInputChars = maxInputChars;
        this.aiEnabled = aiEnabled;
    }

    @Transactional
    public void processResumeWithAi(UUID resumeId, User currentUser, boolean consent) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found"));

        resumeService.validateOwner(resume, currentUser);

        if (!consent) {
            throw new IllegalArgumentException("Consent is required to process resume with external AI services");
        }

        resume.setParsingStatus(ParsingStatus.PROCESSING);
        resumeRepository.saveAndFlush(resume);

        String text = resume.getExtractedText();
        if (text == null || text.trim().isEmpty()) {
            // If text is not extracted, extract it deterministically first
            try {
                resumeService.processResume(resumeId, currentUser);
                // Reload resume
                resume = resumeRepository.findById(resumeId).orElseThrow();
                text = resume.getExtractedText();
            } catch (Exception e) {
                resume.setParsingStatus(ParsingStatus.FAILED);
                resumeRepository.save(resume);
                throw new RuntimeException("Initial text extraction failed for AI processing: " + e.getMessage(), e);
            }
        }

        if (!aiEnabled) {
            // Fallback immediately to deterministic processing
            runDeterministicFallback(resume, currentUser);
            return;
        }

        try {
            // Truncate and sanitize input
            String sanitizedText = sanitizeInput(text);
            String systemPrompt = "You are a professional resume parser. Return a structured JSON object containing a list of matching skills with their predicted proficiency level ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', or 'EXPERT') and a 'confidence' score between 0.0 and 1.0. Also include a 'categories' list of career categories and optional 'yearsOfExperience' if clear. Response format:\n" +
                    "{\n" +
                    "  \"skills\": [\n" +
                    "    {\"name\": \"Java\", \"proficiency\": \"ADVANCED\", \"confidence\": 0.9}\n" +
                    "  ],\n" +
                    "  \"categories\": [\"Backend Development\"],\n" +
                    "  \"yearsOfExperience\": 5\n" +
                    "}";

            String aiResponse = aiProvider.generateChatCompletion(systemPrompt, sanitizedText);
            parseAndEnrichSkills(aiResponse, currentUser);
            
            resume.setParsingStatus(ParsingStatus.COMPLETED);
            resumeRepository.saveAndFlush(resume);

        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to deterministic parser
            runDeterministicFallback(resume, currentUser);
        }
    }

    private void runDeterministicFallback(Resume resume, User user) {
        try {
            // Reuse deterministic parser matching logic
            // Directly trigger standard parsing which extracts and saves skills deterministically
            resumeService.processResume(resume.getId(), user);
            resumeRepository.flush();
        } catch (Exception ex) {
            resume.setParsingStatus(ParsingStatus.FAILED);
            resumeRepository.saveAndFlush(resume);
            throw new RuntimeException("AI processing failed and deterministic fallback also failed: " + ex.getMessage(), ex);
        }
    }

    private String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        // Truncate
        if (input.length() > maxInputChars) {
            return input.substring(0, maxInputChars);
        }
        return input;
    }

    private void parseAndEnrichSkills(String jsonContent, User user) throws Exception {
        JsonNode root = objectMapper.readTree(jsonContent);
        JsonNode skillsNode = root.path("skills");

        if (skillsNode.isArray()) {
            List<Skill> allSkills = skillRepository.findAll();

            for (JsonNode skillNode : skillsNode) {
                String name = skillNode.path("name").asText();
                String proficiencyStr = skillNode.path("proficiency").asText("BEGINNER");
                double confidence = skillNode.path("confidence").asDouble(0.0);

                if (name == null || name.trim().isEmpty() || confidence < 0.5) {
                    continue; // Skip low confidence or empty names
                }

                // Match with existing skills (case insensitive)
                Optional<Skill> matchingSkill = allSkills.stream()
                        .filter(s -> s.getName().equalsIgnoreCase(name.trim()))
                        .findFirst();

                if (matchingSkill.isPresent()) {
                    Skill skill = matchingSkill.get();
                    ProficiencyLevel level;
                    try {
                        level = ProficiencyLevel.valueOf(proficiencyStr.toUpperCase().trim());
                    } catch (IllegalArgumentException ex) {
                        level = ProficiencyLevel.BEGINNER;
                    }

                    // Check for existing UserSkill to avoid duplicates using composite key directly
                    com.arclume.api.domain.UserSkillId userSkillId = new com.arclume.api.domain.UserSkillId(user.getId(), skill.getId());
                    Optional<UserSkill> existing = userSkillRepository.findById(userSkillId);

                    if (existing.isPresent()) {
                        UserSkill us = existing.get();
                        us.setProficiencyLevel(level);
                        userSkillRepository.saveAndFlush(us);
                    } else {
                        UserSkill us = new UserSkill(user, skill, level);
                        userSkillRepository.saveAndFlush(us);
                    }
                }
            }
        }
    }
}

package com.arclume.api.service.ai;

import com.arclume.api.domain.Job;
import com.arclume.api.domain.Skill;
import com.arclume.api.domain.User;
import com.arclume.api.domain.UserSkill;
import com.arclume.api.dto.CareerMatchResponse;
import com.arclume.api.repository.JobRepository;
import com.arclume.api.repository.SkillRepository;
import com.arclume.api.repository.UserSkillRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CareerMatchingService {

    private final JobRepository jobRepository;
    private final SkillRepository skillRepository;
    private final UserSkillRepository userSkillRepository;

    public CareerMatchingService(
            JobRepository jobRepository,
            SkillRepository skillRepository,
            UserSkillRepository userSkillRepository) {
        this.jobRepository = jobRepository;
        this.skillRepository = skillRepository;
        this.userSkillRepository = userSkillRepository;
    }

    public CareerMatchResponse matchUserToJob(UUID jobId, User user) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        // 1. Get user verified skills
        List<UserSkill> userSkills = userSkillRepository.findByUserId(user.getId());
        List<String> userSkillNames = userSkills.stream()
                .map(us -> us.getSkill().getName().toLowerCase())
                .collect(Collectors.toList());

        // 2. Identify job required skills from database using case-insensitive regex boundaries
        List<Skill> allSkills = skillRepository.findAll();
        List<String> jobRequiredSkills = new ArrayList<>();

        String jobText = (job.getTitle() + " " + job.getDescription() + " " + job.getRequirements()).toLowerCase();

        for (Skill skill : allSkills) {
            String skillName = skill.getName().toLowerCase();
            String patternString = "\\b" + Pattern.quote(skillName) + "\\b";
            Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);

            if (pattern.matcher(jobText).find()) {
                jobRequiredSkills.add(skill.getName());
            }
        }

        // 3. Compute matched & missing
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String reqSkill : jobRequiredSkills) {
            if (userSkillNames.contains(reqSkill.toLowerCase())) {
                matched.add(reqSkill);
            } else {
                missing.add(reqSkill);
            }
        }

        // 4. Calculate score
        int score;
        if (jobRequiredSkills.isEmpty()) {
            score = 100;
        } else {
            score = (matched.size() * 100) / jobRequiredSkills.size();
        }

        // 5. Generate explanation
        String explanation;
        if (score == 100) {
            explanation = "Excellent match! You possess all " + matched.size() + " skills required for this job.";
        } else if (score >= 70) {
            explanation = "Strong match. You have " + matched.size() + " out of " + jobRequiredSkills.size() + " required skills. Consider learning: " + String.join(", ", missing) + ".";
        } else if (score >= 40) {
            explanation = "Moderate match. You match some of the requirements. Missing key skills: " + String.join(", ", missing) + ".";
        } else {
            explanation = "Low match. This job requires specialized skills you haven't listed yet. Missing skills: " + String.join(", ", missing) + ".";
        }

        CareerMatchResponse response = new CareerMatchResponse();
        response.setJobId(job.getId());
        response.setJobTitle(job.getTitle());
        response.setCompanyName(job.getCompany());
        response.setMatchedSkills(matched);
        response.setMissingSkills(missing);
        response.setMatchScore(score);
        response.setExplanation(explanation);

        return response;
    }
}

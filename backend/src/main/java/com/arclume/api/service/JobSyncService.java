package com.arclume.api.service;

import com.arclume.api.client.JobClient;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.Job;
import com.arclume.api.domain.WorkMode;
import com.arclume.api.dto.JobSyncSummary;
import com.arclume.api.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
public class JobSyncService {

    private final JobClient jobClient;
    private final JobRepository jobRepository;

    public JobSyncService(JobClient jobClient, JobRepository jobRepository) {
        this.jobClient = jobClient;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public JobSyncSummary syncJobs() {
        int fetchedCount = 0;
        int insertedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        List<JobClient.RemotiveJob> remotiveJobs;
        try {
            remotiveJobs = jobClient.fetchJobs();
            fetchedCount = remotiveJobs.size();
        } catch (Exception e) {
            // Failed to fetch entire payload
            return new JobSyncSummary(0, 0, 0, 0, 1);
        }

        for (JobClient.RemotiveJob rJob : remotiveJobs) {
            try {
                if (rJob.getId() == null || rJob.getId().trim().isEmpty() ||
                        rJob.getTitle() == null || rJob.getTitle().trim().isEmpty() ||
                        rJob.getCompanyName() == null || rJob.getCompanyName().trim().isEmpty()) {
                    skippedCount++;
                    continue;
                }

                String externalId = rJob.getId();
                String sourceProvider = "REMOTIVE";

                Optional<Job> existingJobOpt = jobRepository.findBySourceProviderAndExternalId(sourceProvider, externalId);
                Job job;
                boolean isUpdate = false;

                if (existingJobOpt.isPresent()) {
                    job = existingJobOpt.get();
                    isUpdate = true;
                } else {
                    job = new Job();
                    job.setExternalId(externalId);
                    job.setSourceProvider(sourceProvider);
                }

                job.setTitle(rJob.getTitle());
                job.setCompany(rJob.getCompanyName());
                job.setLocation(rJob.getCandidateRequiredLocation());
                job.setExternalUrl(rJob.getUrl());
                job.setSalaryRange(rJob.getSalary());
                job.setWorkMode(WorkMode.REMOTE); // Remotive offers remote jobs
                job.setEmploymentType(mapEmploymentType(rJob.getJobType()));
                job.setDescription(sanitizeHtml(rJob.getDescription()));
                job.setRequirements(""); // Remotive doesn't have a distinct requirements field, we can default it or extract
                job.setPostedAt(parseInstant(rJob.getPublicationDate()));
                job.setSyncedAt(Instant.now());
                job.setActive(true);

                jobRepository.save(job);

                if (isUpdate) {
                    updatedCount++;
                } else {
                    insertedCount++;
                }

            } catch (Exception e) {
                failedCount++;
            }
        }

        return new JobSyncSummary(fetchedCount, insertedCount, updatedCount, skippedCount, failedCount);
    }

    private EmploymentType mapEmploymentType(String jobType) {
        if (jobType == null) return null;
        String normalized = jobType.toLowerCase().replace("-", "_").replace(" ", "_");
        switch (normalized) {
            case "full_time":
            case "fulltime":
                return EmploymentType.FULL_TIME;
            case "part_time":
            case "parttime":
                return EmploymentType.PART_TIME;
            case "contract":
                return EmploymentType.CONTRACT;
            case "internship":
                return EmploymentType.INTERNSHIP;
            default:
                return null;
        }
    }

    private String sanitizeHtml(String html) {
        if (html == null) return null;
        // Securely clean HTML to filter scripts, handler attributes, and dangerous schemas
        return org.jsoup.Jsoup.clean(html, org.jsoup.safety.Safelist.basic());
    }

    private Instant parseInstant(String dateStr) {
        if (dateStr == null) return null;
        try {
            return Instant.parse(dateStr);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateStr).toInstant(ZoneOffset.UTC);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}

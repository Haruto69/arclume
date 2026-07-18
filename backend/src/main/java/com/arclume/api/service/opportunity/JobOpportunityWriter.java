package com.arclume.api.service.opportunity;

import com.arclume.api.domain.Job;
import com.arclume.api.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class JobOpportunityWriter {

    private final JobRepository jobRepository;

    public JobOpportunityWriter(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OpportunityPersistResult upsert(String providerKey, NormalizedJobOpportunity record, Instant syncedAt) {
        Job job = jobRepository.findBySourceProviderAndExternalId(providerKey, record.sourceId())
                .orElseGet(Job::new);
        boolean created = job.getId() == null;

        if (created) {
            job.setExternalId(record.sourceId());
            job.setSourceProvider(providerKey);
        }

        job.setTitle(record.title());
        job.setCompany(record.company());
        job.setLocation(record.location());
        job.setExternalUrl(record.sourceUrl());
        job.setAttributionLabel(record.attributionLabel());
        job.setSalaryRange(record.salaryRange());
        job.setWorkMode(record.workMode());
        job.setEmploymentType(record.employmentType());
        job.setDescription(record.description());
        job.setRequirements(record.requirements());
        job.setPostedAt(record.postedAt());
        job.setSyncedAt(syncedAt);
        job.setActive(record.active());

        jobRepository.save(job);
        return created ? OpportunityPersistResult.CREATED : OpportunityPersistResult.UPDATED;
    }
}

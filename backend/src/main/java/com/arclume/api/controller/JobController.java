package com.arclume.api.controller;

import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.Job;
import com.arclume.api.domain.WorkMode;
import com.arclume.api.dto.JobSyncSummary;
import com.arclume.api.repository.JobRepository;
import com.arclume.api.repository.JobSpecifications;
import com.arclume.api.service.JobSyncService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobRepository jobRepository;
    private final JobSyncService jobSyncService;

    public JobController(JobRepository jobRepository, JobSyncService jobSyncService) {
        this.jobRepository = jobRepository;
        this.jobSyncService = jobSyncService;
    }

    @GetMapping
    public ResponseEntity<Page<Job>> getJobs(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) WorkMode workMode,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, limit);

        Specification<Job> spec = Specification.where(JobSpecifications.hasTitle(title))
                .and(JobSpecifications.hasCompany(company))
                .and(JobSpecifications.hasLocation(location))
                .and(JobSpecifications.hasWorkMode(workMode))
                .and(JobSpecifications.hasEmploymentType(employmentType));

        Page<Job> jobs = jobRepository.findAll(spec, pageable);

        return ResponseEntity.ok()
                .header("X-Job-Attribution", "Jobs provided by Remotive API (https://remotive.com/api/remote-jobs)")
                .body(jobs);
    }

    @PostMapping("/sync")
    public ResponseEntity<JobSyncSummary> sync() {
        JobSyncSummary summary = jobSyncService.syncJobs();
        return ResponseEntity.ok(summary);
    }
}

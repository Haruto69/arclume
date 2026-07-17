package com.arclume.api.service;

import com.arclume.api.domain.Application;
import com.arclume.api.domain.ApplicationStatus;
import com.arclume.api.domain.Job;
import com.arclume.api.domain.User;
import com.arclume.api.dto.ApplicationCreateRequest;
import com.arclume.api.dto.ApplicationResponse;
import com.arclume.api.dto.ApplicationSummaryResponse;
import com.arclume.api.dto.ApplicationUpdateRequest;
import com.arclume.api.repository.ApplicationRepository;
import com.arclume.api.repository.JobRepository;
import com.arclume.api.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            JobRepository jobRepository,
            UserRepository userRepository) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> list(
            User user,
            ApplicationStatus status,
            int page,
            int size,
            String sort) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                parseSort(sort)
        );
        Page<Application> applications = status == null
                ? applicationRepository.findByUserId(user.getId(), pageable)
                : applicationRepository.findByUserIdAndStatus(user.getId(), status, pageable);
        return applications.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(User user, UUID applicationId) {
        return toResponse(findOwned(user, applicationId));
    }

    @Transactional
    public CreateResult create(User user, ApplicationCreateRequest request) {
        User lockedUser = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new AccessDeniedException("User is not available"));
        Application existing = applicationRepository.findByUserIdAndJobId(lockedUser.getId(), request.jobId())
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == ApplicationStatus.SAVED && request.status() == ApplicationStatus.APPLIED) {
                existing.setStatus(ApplicationStatus.APPLIED);
                existing.setAppliedAt(resolveAppliedAt(ApplicationStatus.APPLIED, request.appliedAt()));
                if (request.notes() != null) {
                    existing.setNotes(normalizeNotes(request.notes()));
                }
                existing = applicationRepository.saveAndFlush(existing);
            }
            return new CreateResult(toResponse(existing), false);
        }
        Job job = jobRepository.findById(request.jobId())
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        if (!Boolean.TRUE.equals(job.getActive())) {
            throw new IllegalArgumentException("Cannot track an inactive job");
        }

        Application application = new Application();
        application.setUser(lockedUser);
        application.setJob(job);
        ApplicationStatus status = request.status() == null ? ApplicationStatus.SAVED : request.status();
        application.setStatus(status);
        application.setAppliedAt(resolveAppliedAt(status, request.appliedAt()));
        application.setNotes(normalizeNotes(request.notes()));

        Application saved = applicationRepository.saveAndFlush(application);
        return new CreateResult(toResponse(saved), true);
    }

    @Transactional
    public ApplicationResponse update(User user, UUID applicationId, ApplicationUpdateRequest request) {
        Application application = findOwned(user, applicationId);
        if (Boolean.TRUE.equals(request.clearAppliedAt()) && request.appliedAt() != null) {
            throw new IllegalArgumentException("Applied date cannot be set and cleared in the same request");
        }

        if (request.status() != null) {
            application.setStatus(request.status());
        }
        if (Boolean.TRUE.equals(request.clearAppliedAt())) {
            application.setAppliedAt(null);
        } else if (request.appliedAt() != null) {
            application.setAppliedAt(request.appliedAt());
        } else if (request.status() == ApplicationStatus.APPLIED && application.getAppliedAt() == null) {
            application.setAppliedAt(Instant.now());
        }
        if (request.notes() != null) {
            application.setNotes(normalizeNotes(request.notes()));
        }

        return toResponse(applicationRepository.saveAndFlush(application));
    }

    @Transactional
    public void delete(User user, UUID applicationId) {
        applicationRepository.delete(findOwned(user, applicationId));
    }

    @Transactional(readOnly = true)
    public ApplicationSummaryResponse summary(User user) {
        Map<ApplicationStatus, Long> byStatus = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            byStatus.put(status, applicationRepository.countByUserIdAndStatus(user.getId(), status));
        }
        return new ApplicationSummaryResponse(applicationRepository.countByUserId(user.getId()), byStatus);
    }

    private Application findOwned(User user, UUID applicationId) {
        return applicationRepository.findByIdAndUserId(applicationId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Application not found"));
    }

    private Instant resolveAppliedAt(ApplicationStatus status, Instant requestedAppliedAt) {
        if (requestedAppliedAt != null) {
            return requestedAppliedAt;
        }
        return status == ApplicationStatus.APPLIED ? Instant.now() : null;
    }

    private String normalizeNotes(String notes) {
        return notes == null || notes.isBlank() ? null : notes;
    }

    private Sort parseSort(String sort) {
        String[] parts = sort == null ? new String[0] : sort.split(",", 2);
        String property = switch (parts.length == 0 ? "" : parts[0]) {
            case "createdAt", "created_at" -> "createdAt";
            case "appliedAt", "applied_at" -> "appliedAt";
            case "status" -> "status";
            default -> "updatedAt";
        };
        Sort.Direction direction = parts.length > 1
                ? Sort.Direction.fromOptionalString(parts[1]).orElse(Sort.Direction.DESC)
                : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    private ApplicationResponse toResponse(Application application) {
        Job job = application.getJob();
        return new ApplicationResponse(
                application.getId(),
                job.getId(),
                job.getTitle(),
                job.getCompany(),
                job.getLocation(),
                job.getEmploymentType(),
                job.getWorkMode(),
                job.getExternalUrl(),
                job.getSourceProvider(),
                application.getStatus(),
                application.getAppliedAt(),
                application.getNotes(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }

    public record CreateResult(ApplicationResponse application, boolean created) {
    }
}

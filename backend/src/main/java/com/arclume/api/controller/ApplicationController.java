package com.arclume.api.controller;

import com.arclume.api.domain.ApplicationStatus;
import com.arclume.api.domain.User;
import com.arclume.api.dto.ApplicationCreateRequest;
import com.arclume.api.dto.ApplicationResponse;
import com.arclume.api.dto.ApplicationSummaryResponse;
import com.arclume.api.dto.ApplicationUpdateRequest;
import com.arclume.api.service.ApplicationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public ResponseEntity<Page<ApplicationResponse>> list(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort) {
        return ResponseEntity.ok(applicationService.list(getCurrentUser(), status, page, size, sort));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApplicationSummaryResponse> summary() {
        return ResponseEntity.ok(applicationService.summary(getCurrentUser()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(applicationService.get(getCurrentUser(), id));
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody ApplicationCreateRequest request) {
        ApplicationService.CreateResult result = applicationService.create(getCurrentUser(), request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.application());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApplicationResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ApplicationUpdateRequest request) {
        return ResponseEntity.ok(applicationService.update(getCurrentUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        applicationService.delete(getCurrentUser(), id);
        return ResponseEntity.noContent().build();
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        throw new InsufficientAuthenticationException("User is not authenticated");
    }
}

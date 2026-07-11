package com.arclume.api.controller;

import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.RecommendationStatus;
import com.arclume.api.domain.User;
import com.arclume.api.domain.WorkMode;
import com.arclume.api.dto.RecommendationRefreshResponse;
import com.arclume.api.dto.RecommendationResponse;
import com.arclume.api.dto.RecommendationStatusRequest;
import com.arclume.api.service.JobRecommendationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
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
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final JobRecommendationService recommendationService;

    public RecommendationController(JobRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public ResponseEntity<Page<RecommendationResponse>> list(
            @RequestParam(required = false) RecommendationStatus status,
            @RequestParam(required = false) Integer minimumScore,
            @RequestParam(required = false) WorkMode workMode,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "generatedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        return ResponseEntity.ok(recommendationService.listRecommendations(
                getCurrentUser(),
                status,
                minimumScore,
                workMode,
                employmentType,
                company,
                location,
                page,
                size,
                sortBy,
                direction
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecommendationResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(recommendationService.getRecommendation(getCurrentUser(), id));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RecommendationRefreshResponse> refresh() {
        return ResponseEntity.ok(recommendationService.refreshRecommendations(getCurrentUser()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<RecommendationResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody RecommendationStatusRequest request) {
        return ResponseEntity.ok(recommendationService.updateStatus(getCurrentUser(), id, request.getStatus()));
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        throw new InsufficientAuthenticationException("User is not authenticated");
    }
}

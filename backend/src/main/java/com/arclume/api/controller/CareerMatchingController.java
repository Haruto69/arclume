package com.arclume.api.controller;

import com.arclume.api.domain.User;
import com.arclume.api.dto.CareerMatchResponse;
import com.arclume.api.service.ai.CareerMatchingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class CareerMatchingController {

    private final CareerMatchingService careerMatchingService;

    public CareerMatchingController(CareerMatchingService careerMatchingService) {
        this.careerMatchingService = careerMatchingService;
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        throw new org.springframework.security.authentication.InsufficientAuthenticationException("User is not authenticated");
    }

    @PostMapping("/{id}/match")
    public ResponseEntity<?> matchJob(@PathVariable UUID id) {
        try {
            User currentUser = getCurrentUser();
            CareerMatchResponse match = careerMatchingService.matchUserToJob(id, currentUser);
            return ResponseEntity.ok(match);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Career matching failed: " + e.getMessage());
        }
    }
}

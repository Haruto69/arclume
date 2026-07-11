package com.arclume.api.controller;

import com.arclume.api.domain.Resume;
import com.arclume.api.domain.User;
import com.arclume.api.dto.ResumeResponse;
import com.arclume.api.service.ResumeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeController {

    private final ResumeService resumeService;
    private final com.arclume.api.service.ai.AiResumeService aiResumeService;

    public ResumeController(
            ResumeService resumeService,
            com.arclume.api.service.ai.AiResumeService aiResumeService) {
        this.resumeService = resumeService;
        this.aiResumeService = aiResumeService;
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        throw new org.springframework.security.authentication.InsufficientAuthenticationException("User is not authenticated");
    }

    @PostMapping
    public ResponseEntity<?> uploadResume(@RequestParam("file") MultipartFile file) {
        try {
            User currentUser = getCurrentUser();
            Resume resume = resumeService.uploadResume(file, currentUser);
            return ResponseEntity.status(HttpStatus.CREATED).body(new ResumeResponse(resume));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload failed: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<ResumeResponse>> getResumes() {
        User currentUser = getCurrentUser();
        List<ResumeResponse> list = resumeService.getUserResumes(currentUser).stream()
                .map(ResumeResponse::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getResume(@PathVariable UUID id) {
        try {
            User currentUser = getCurrentUser();
            Resume resume = resumeService.getResume(id, currentUser);
            return ResponseEntity.ok(new ResumeResponse(resume));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<?> processResume(@PathVariable UUID id) {
        try {
            User currentUser = getCurrentUser();
            resumeService.processResume(id, currentUser);
            Resume updated = resumeService.getResume(id, currentUser);
            return ResponseEntity.ok(new ResumeResponse(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/{id}/ai-process")
    public ResponseEntity<?> aiProcessResume(
            @PathVariable UUID id,
            @RequestBody com.arclume.api.dto.AiProcessRequest request) {
        try {
            User currentUser = getCurrentUser();
            aiResumeService.processResumeWithAi(id, currentUser, request.isConsent());
            Resume updated = resumeService.getResume(id, currentUser);
            return ResponseEntity.ok(new ResumeResponse(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            // Do not leak external provider details to clients
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("AI processing failed. Details have been safely logged.");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteResume(@PathVariable UUID id) {
        try {
            User currentUser = getCurrentUser();
            resumeService.deleteResume(id, currentUser);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}

package com.arclume.api.dto;

import com.arclume.api.domain.ParsingStatus;
import com.arclume.api.domain.Resume;

import java.time.Instant;
import java.util.UUID;

public class ResumeResponse {
    private UUID id;
    private UUID userId;
    private String filename;
    private String contentType;
    private ParsingStatus parsingStatus;
    private String extractedText;
    private Instant createdAt;
    private Instant updatedAt;

    public ResumeResponse() {}

    public ResumeResponse(Resume resume) {
        this.id = resume.getId();
        this.userId = resume.getUser().getId();
        this.filename = resume.getFilename();
        this.contentType = resume.getContentType();
        this.parsingStatus = resume.getParsingStatus();
        this.extractedText = resume.getExtractedText();
        this.createdAt = resume.getCreatedAt();
        this.updatedAt = resume.getUpdatedAt();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public ParsingStatus getParsingStatus() {
        return parsingStatus;
    }

    public void setParsingStatus(ParsingStatus parsingStatus) {
        this.parsingStatus = parsingStatus;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

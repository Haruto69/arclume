package com.arclume.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "resumes")
public class Resume extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String filename;

    @NotBlank
    @Size(max = 100)
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @NotBlank
    @Size(max = 1000)
    @Column(name = "storage_ref", nullable = false, length = 1000)
    private String storageRef;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "parsing_status", nullable = false, length = 50)
    private ParsingStatus parsingStatus = ParsingStatus.PENDING;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public String getStorageRef() {
        return storageRef;
    }

    public void setStorageRef(String storageRef) {
        this.storageRef = storageRef;
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
}

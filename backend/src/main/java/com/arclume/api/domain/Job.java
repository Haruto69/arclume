package com.arclume.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "jobs")
public class Job extends BaseEntity {

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String title;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String company;

    @Size(max = 200)
    @Column(length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 50)
    private EmploymentType employmentType;

    @Size(max = 1000)
    @Column(name = "external_url", length = 1000)
    private String externalUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String requirements;

    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "job")
    private List<Application> applications = new ArrayList<>();

    @Size(max = 100)
    @Column(name = "external_id", length = 100)
    private String externalId;

    @Size(max = 50)
    @Column(name = "source_provider", length = 50)
    private String sourceProvider;

    @Size(max = 255)
    @Column(name = "salary_range", length = 255)
    private String salaryRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", length = 50)
    private WorkMode workMode = WorkMode.REMOTE;

    @Column(name = "posted_at")
    private java.time.Instant postedAt;

    @Column(name = "synced_at")
    private java.time.Instant syncedAt;

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    public void setSourceProvider(String sourceProvider) {
        this.sourceProvider = sourceProvider;
    }

    public String getSalaryRange() {
        return salaryRange;
    }

    public void setSalaryRange(String salaryRange) {
        this.salaryRange = salaryRange;
    }

    public WorkMode getWorkMode() {
        return workMode;
    }

    public void setWorkMode(WorkMode workMode) {
        this.workMode = workMode;
    }

    public java.time.Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(java.time.Instant postedAt) {
        this.postedAt = postedAt;
    }

    public java.time.Instant getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(java.time.Instant syncedAt) {
        this.syncedAt = syncedAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(EmploymentType employmentType) {
        this.employmentType = employmentType;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRequirements() {
        return requirements;
    }

    public void setRequirements(String requirements) {
        this.requirements = requirements;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    public List<Application> getApplications() {
        return applications;
    }

    public void setApplications(List<Application> applications) {
        this.applications = applications;
    }
}

package com.arclume.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_profiles")
public class UserProfile extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Size(max = 160)
    @Column(length = 160)
    private String headline;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Size(max = 100)
    @Column(length = 100)
    private String city;

    @Size(max = 100)
    @Column(length = 100)
    private String state;

    @Size(max = 100)
    @Column(length = 100)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "education_level", length = 50)
    private EducationLevel educationLevel;

    @Size(max = 200)
    @Column(length = 200)
    private String institution;

    @Size(max = 200)
    @Column(name = "field_of_study", length = 200)
    private String fieldOfStudy;

    @Min(1950)
    @Max(2100)
    @Column(name = "graduation_year")
    private Integer graduationYear;

    @Min(0)
    @Max(60)
    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Size(max = 160)
    @Column(name = "\"current_role\"", length = 160)
    private String currentRole;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "desired_roles", nullable = false, columnDefinition = "jsonb")
    private List<String> desiredRoles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_locations", nullable = false, columnDefinition = "jsonb")
    private List<String> preferredLocations = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_work_modes", nullable = false, columnDefinition = "jsonb")
    private List<WorkMode> preferredWorkModes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_employment_types", nullable = false, columnDefinition = "jsonb")
    private List<EmploymentType> preferredEmploymentTypes = new ArrayList<>();

    @Column(name = "open_to_relocation", nullable = false)
    private boolean openToRelocation;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public EducationLevel getEducationLevel() {
        return educationLevel;
    }

    public void setEducationLevel(EducationLevel educationLevel) {
        this.educationLevel = educationLevel;
    }

    public String getInstitution() {
        return institution;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public String getFieldOfStudy() {
        return fieldOfStudy;
    }

    public void setFieldOfStudy(String fieldOfStudy) {
        this.fieldOfStudy = fieldOfStudy;
    }

    public Integer getGraduationYear() {
        return graduationYear;
    }

    public void setGraduationYear(Integer graduationYear) {
        this.graduationYear = graduationYear;
    }

    public Integer getYearsExperience() {
        return yearsExperience;
    }

    public void setYearsExperience(Integer yearsExperience) {
        this.yearsExperience = yearsExperience;
    }

    public String getCurrentRole() {
        return currentRole;
    }

    public void setCurrentRole(String currentRole) {
        this.currentRole = currentRole;
    }

    public List<String> getDesiredRoles() {
        return List.copyOf(desiredRoles);
    }

    public void setDesiredRoles(List<String> desiredRoles) {
        this.desiredRoles = desiredRoles == null ? new ArrayList<>() : new ArrayList<>(desiredRoles);
    }

    public List<String> getPreferredLocations() {
        return List.copyOf(preferredLocations);
    }

    public void setPreferredLocations(List<String> preferredLocations) {
        this.preferredLocations = preferredLocations == null ? new ArrayList<>() : new ArrayList<>(preferredLocations);
    }

    public List<WorkMode> getPreferredWorkModes() {
        return List.copyOf(preferredWorkModes);
    }

    public void setPreferredWorkModes(List<WorkMode> preferredWorkModes) {
        this.preferredWorkModes = preferredWorkModes == null ? new ArrayList<>() : new ArrayList<>(preferredWorkModes);
    }

    public List<EmploymentType> getPreferredEmploymentTypes() {
        return List.copyOf(preferredEmploymentTypes);
    }

    public void setPreferredEmploymentTypes(List<EmploymentType> preferredEmploymentTypes) {
        this.preferredEmploymentTypes = preferredEmploymentTypes == null
                ? new ArrayList<>()
                : new ArrayList<>(preferredEmploymentTypes);
    }

    public boolean isOpenToRelocation() {
        return openToRelocation;
    }

    public void setOpenToRelocation(boolean openToRelocation) {
        this.openToRelocation = openToRelocation;
    }
}

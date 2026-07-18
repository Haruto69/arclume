package com.arclume.api.dto;

import com.arclume.api.domain.EducationLevel;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.WorkMode;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class UpdateUserProfileRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must be 100 characters or fewer")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must be 100 characters or fewer")
    private String lastName;

    @Size(max = 160, message = "Headline must be 160 characters or fewer")
    private String headline;

    @Size(max = 2000, message = "Bio must be 2000 characters or fewer")
    private String bio;

    @Size(max = 100, message = "City must be 100 characters or fewer")
    private String city;

    @Size(max = 100, message = "State or region must be 100 characters or fewer")
    private String state;

    @Size(max = 100, message = "Country must be 100 characters or fewer")
    private String country;

    private EducationLevel educationLevel;

    @Size(max = 200, message = "Institution must be 200 characters or fewer")
    private String institution;

    @Size(max = 200, message = "Field of study must be 200 characters or fewer")
    private String fieldOfStudy;

    @Min(value = 1950, message = "Graduation year must be between 1950 and 2100")
    @Max(value = 2100, message = "Graduation year must be between 1950 and 2100")
    private Integer graduationYear;

    @Min(value = 0, message = "Years of experience must be between 0 and 60")
    @Max(value = 60, message = "Years of experience must be between 0 and 60")
    private Integer yearsExperience;

    @Size(max = 160, message = "Current role must be 160 characters or fewer")
    private String currentRole;

    @Size(max = 10, message = "Desired roles can contain at most 10 entries")
    private List<String> desiredRoles;

    @Size(max = 10, message = "Preferred locations can contain at most 10 entries")
    private List<String> preferredLocations;

    private List<WorkMode> preferredWorkModes;

    private List<EmploymentType> preferredEmploymentTypes;

    private boolean openToRelocation;

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("Unknown profile field: " + fieldName);
    }
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
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
        return desiredRoles;
    }

    public void setDesiredRoles(List<String> desiredRoles) {
        this.desiredRoles = desiredRoles;
    }

    public List<String> getPreferredLocations() {
        return preferredLocations;
    }

    public void setPreferredLocations(List<String> preferredLocations) {
        this.preferredLocations = preferredLocations;
    }

    public List<WorkMode> getPreferredWorkModes() {
        return preferredWorkModes;
    }

    public void setPreferredWorkModes(List<WorkMode> preferredWorkModes) {
        this.preferredWorkModes = preferredWorkModes;
    }

    public List<EmploymentType> getPreferredEmploymentTypes() {
        return preferredEmploymentTypes;
    }

    public void setPreferredEmploymentTypes(List<EmploymentType> preferredEmploymentTypes) {
        this.preferredEmploymentTypes = preferredEmploymentTypes;
    }

    public boolean isOpenToRelocation() {
        return openToRelocation;
    }

    public void setOpenToRelocation(boolean openToRelocation) {
        this.openToRelocation = openToRelocation;
    }
}

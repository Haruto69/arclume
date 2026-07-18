package com.arclume.api.dto;

import com.arclume.api.domain.EducationLevel;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.WorkMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
        UUID profileId,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String headline,
        String bio,
        String city,
        String state,
        String country,
        EducationLevel educationLevel,
        String institution,
        String fieldOfStudy,
        Integer graduationYear,
        Integer yearsExperience,
        String currentRole,
        List<String> desiredRoles,
        List<String> preferredLocations,
        List<WorkMode> preferredWorkModes,
        List<EmploymentType> preferredEmploymentTypes,
        boolean openToRelocation,
        Instant createdAt,
        Instant updatedAt
) {
    public UserProfileResponse {
        desiredRoles = desiredRoles == null ? List.of() : List.copyOf(desiredRoles);
        preferredLocations = preferredLocations == null ? List.of() : List.copyOf(preferredLocations);
        preferredWorkModes = preferredWorkModes == null ? List.of() : List.copyOf(preferredWorkModes);
        preferredEmploymentTypes = preferredEmploymentTypes == null ? List.of() : List.copyOf(preferredEmploymentTypes);
    }
}

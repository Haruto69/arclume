package com.arclume.api.service;

import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.User;
import com.arclume.api.domain.UserProfile;
import com.arclume.api.domain.WorkMode;
import com.arclume.api.dto.UpdateUserProfileRequest;
import com.arclume.api.dto.UserProfileResponse;
import com.arclume.api.repository.UserProfileRepository;
import com.arclume.api.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class UserProfileService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_HEADLINE_LENGTH = 160;
    private static final int MAX_BIO_LENGTH = 2000;
    private static final int MAX_LOCATION_PART_LENGTH = 100;
    private static final int MAX_EDUCATION_TEXT_LENGTH = 200;
    private static final int MAX_CURRENT_ROLE_LENGTH = 160;
    private static final int MAX_COLLECTION_ENTRIES = 10;
    private static final int MAX_COLLECTION_ENTRY_LENGTH = 160;

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    public UserProfileService(UserRepository userRepository, UserProfileRepository userProfileRepository) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(User authenticatedUser) {
        User user = userRepository.findById(authenticatedUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user no longer exists"));
        return userProfileRepository.findByUserId(user.getId())
                .map(profile -> toResponse(user, profile))
                .orElseGet(() -> emptyResponse(user));
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID authenticatedUserId, UpdateUserProfileRequest request) {
        User user = userRepository.findByIdForUpdate(authenticatedUserId)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user no longer exists"));

        user.setFirstName(requiredTrimmed(request.getFirstName(), "First name", MAX_NAME_LENGTH));
        user.setLastName(requiredTrimmed(request.getLastName(), "Last name", MAX_NAME_LENGTH));

        UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElseGet(UserProfile::new);
        profile.setUser(user);
        profile.setHeadline(optionalTrimmed(request.getHeadline(), "Headline", MAX_HEADLINE_LENGTH));
        profile.setBio(optionalTrimmed(request.getBio(), "Bio", MAX_BIO_LENGTH));
        profile.setCity(optionalTrimmed(request.getCity(), "City", MAX_LOCATION_PART_LENGTH));
        profile.setState(optionalTrimmed(request.getState(), "State or region", MAX_LOCATION_PART_LENGTH));
        profile.setCountry(optionalTrimmed(request.getCountry(), "Country", MAX_LOCATION_PART_LENGTH));
        profile.setEducationLevel(request.getEducationLevel());
        profile.setInstitution(optionalTrimmed(request.getInstitution(), "Institution", MAX_EDUCATION_TEXT_LENGTH));
        profile.setFieldOfStudy(optionalTrimmed(request.getFieldOfStudy(), "Field of study", MAX_EDUCATION_TEXT_LENGTH));
        profile.setGraduationYear(request.getGraduationYear());
        profile.setYearsExperience(request.getYearsExperience());
        profile.setCurrentRole(optionalTrimmed(request.getCurrentRole(), "Current role", MAX_CURRENT_ROLE_LENGTH));
        profile.setDesiredRoles(normalizedTextList(request.getDesiredRoles(), "Desired roles"));
        profile.setPreferredLocations(normalizedTextList(request.getPreferredLocations(), "Preferred locations"));
        profile.setPreferredWorkModes(normalizedEnumList(request.getPreferredWorkModes(), "Preferred work modes"));
        profile.setPreferredEmploymentTypes(normalizedEnumList(
                request.getPreferredEmploymentTypes(),
                "Preferred employment types"));
        profile.setOpenToRelocation(request.isOpenToRelocation());

        UserProfile saved = userProfileRepository.saveAndFlush(profile);
        userRepository.saveAndFlush(user);
        return toResponse(user, saved);
    }

    private UserProfileResponse emptyResponse(User user) {
        return new UserProfileResponse(
                null,
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                null,
                null
        );
    }

    private UserProfileResponse toResponse(User user, UserProfile profile) {
        return new UserProfileResponse(
                profile.getId(),
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                profile.getHeadline(),
                profile.getBio(),
                profile.getCity(),
                profile.getState(),
                profile.getCountry(),
                profile.getEducationLevel(),
                profile.getInstitution(),
                profile.getFieldOfStudy(),
                profile.getGraduationYear(),
                profile.getYearsExperience(),
                profile.getCurrentRole(),
                profile.getDesiredRoles(),
                profile.getPreferredLocations(),
                profile.getPreferredWorkModes(),
                profile.getPreferredEmploymentTypes(),
                profile.isOpenToRelocation(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    private String requiredTrimmed(String value, String fieldName, int maxLength) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be " + maxLength + " characters or fewer");
        }
        return normalized;
    }

    private String optionalTrimmed(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be " + maxLength + " characters or fewer");
        }
        return normalized;
    }

    private List<String> normalizedTextList(List<String> values, String fieldName) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > MAX_COLLECTION_ENTRIES) {
            throw new IllegalArgumentException(fieldName + " can contain at most 10 entries");
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException(fieldName + " cannot contain null entries");
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException(fieldName + " cannot contain blank entries");
            }
            if (trimmed.length() > MAX_COLLECTION_ENTRY_LENGTH) {
                throw new IllegalArgumentException(fieldName + " entries must be 160 characters or fewer");
            }
            normalized.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return List.copyOf(normalized.values());
    }

    private <T extends Enum<T>> List<T> normalizedEnumList(List<T> values, String fieldName) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<T> normalized = new LinkedHashSet<>();
        for (T value : values) {
            if (value == null) {
                throw new IllegalArgumentException(fieldName + " cannot contain null entries");
            }
            normalized.add(value);
        }
        return List.copyOf(new ArrayList<>(normalized));
    }
}

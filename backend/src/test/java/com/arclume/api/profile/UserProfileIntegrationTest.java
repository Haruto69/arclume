package com.arclume.api.profile;

import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.EducationLevel;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.User;
import com.arclume.api.domain.UserProfile;
import com.arclume.api.domain.WorkMode;
import com.arclume.api.dto.UpdateUserProfileRequest;
import com.arclume.api.dto.UserProfileResponse;
import com.arclume.api.repository.UserProfileRepository;
import com.arclume.api.repository.UserRepository;
import com.arclume.api.repository.UserSessionRepository;
import com.arclume.api.security.AuthCookieService;
import com.arclume.api.security.SessionService;
import com.arclume.api.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserProfileIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "securePassword123";
    private static final Instant EMAIL_VERIFIED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TOTP_ENABLED_AT = Instant.parse("2026-01-02T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User primaryUser;
    private User secondaryUser;
    private Cookie primaryCookie;
    private Cookie secondaryCookie;

    @BeforeEach
    void setUp() {
        userSessionRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();

        primaryUser = user("profile-primary@example.com", Role.USER);
        secondaryUser = user("profile-secondary@example.com", Role.ADMIN);
        primaryCookie = authCookie(primaryUser);
        secondaryCookie = authCookie(secondaryUser);
    }

    @AfterEach
    void tearDown() {
        userSessionRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void profilePersistsScalarsJsonArraysAndSupportedEnums() {
        UserProfile profile = new UserProfile();
        profile.setUser(primaryUser);
        profile.setHeadline("Backend student engineer");
        profile.setBio("Building useful tools.");
        profile.setCity("Bengaluru");
        profile.setState("Karnataka");
        profile.setCountry("India");
        profile.setEducationLevel(EducationLevel.BACHELORS);
        profile.setInstitution("Arclume University");
        profile.setFieldOfStudy("Computer Science");
        profile.setGraduationYear(2027);
        profile.setYearsExperience(2);
        profile.setCurrentRole("Student Developer");
        profile.setDesiredRoles(List.of("Backend Engineer", "Platform Engineer"));
        profile.setPreferredLocations(List.of("Bengaluru", "Remote, India"));
        profile.setPreferredWorkModes(List.of(WorkMode.REMOTE, WorkMode.HYBRID));
        profile.setPreferredEmploymentTypes(List.of(EmploymentType.INTERNSHIP, EmploymentType.FULL_TIME));
        profile.setOpenToRelocation(true);

        UUID profileId = userProfileRepository.saveAndFlush(profile).getId();

        UserProfile reloaded = userProfileRepository.findById(profileId).orElseThrow();
        assertThat(reloaded.getHeadline()).isEqualTo("Backend student engineer");
        assertThat(reloaded.getBio()).isEqualTo("Building useful tools.");
        assertThat(reloaded.getCity()).isEqualTo("Bengaluru");
        assertThat(reloaded.getState()).isEqualTo("Karnataka");
        assertThat(reloaded.getCountry()).isEqualTo("India");
        assertThat(reloaded.getEducationLevel()).isEqualTo(EducationLevel.BACHELORS);
        assertThat(reloaded.getInstitution()).isEqualTo("Arclume University");
        assertThat(reloaded.getFieldOfStudy()).isEqualTo("Computer Science");
        assertThat(reloaded.getGraduationYear()).isEqualTo(2027);
        assertThat(reloaded.getYearsExperience()).isEqualTo(2);
        assertThat(reloaded.getCurrentRole()).isEqualTo("Student Developer");
        assertThat(reloaded.getDesiredRoles()).containsExactly("Backend Engineer", "Platform Engineer");
        assertThat(reloaded.getPreferredLocations()).containsExactly("Bengaluru", "Remote, India");
        assertThat(reloaded.getPreferredWorkModes()).containsExactly(WorkMode.REMOTE, WorkMode.HYBRID);
        assertThat(reloaded.getPreferredEmploymentTypes()).containsExactly(EmploymentType.INTERNSHIP, EmploymentType.FULL_TIME);
        assertThat(reloaded.isOpenToRelocation()).isTrue();
    }

    @Test
    void entityCollectionAccessorsDefensivelyCopyAndDefaultToEmptyLists() {
        UserProfile profile = new UserProfile();
        List<String> desiredRoles = new ArrayList<>(List.of("Backend Engineer"));
        List<WorkMode> workModes = new ArrayList<>(List.of(WorkMode.REMOTE));

        profile.setDesiredRoles(desiredRoles);
        profile.setPreferredWorkModes(workModes);
        desiredRoles.add("Mutated Role");
        workModes.add(WorkMode.HYBRID);

        assertThat(profile.getDesiredRoles()).containsExactly("Backend Engineer");
        assertThat(profile.getPreferredWorkModes()).containsExactly(WorkMode.REMOTE);
        assertThatThrownBy(() -> profile.getDesiredRoles().add("Illegal"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.getPreferredWorkModes().add(WorkMode.ON_SITE))
                .isInstanceOf(UnsupportedOperationException.class);

        profile.setDesiredRoles(null);
        profile.setPreferredLocations(null);
        profile.setPreferredWorkModes(null);
        profile.setPreferredEmploymentTypes(null);

        assertThat(profile.getDesiredRoles()).isEmpty();
        assertThat(profile.getPreferredLocations()).isEmpty();
        assertThat(profile.getPreferredWorkModes()).isEmpty();
        assertThat(profile.getPreferredEmploymentTypes()).isEmpty();
        assertThat(profile.isOpenToRelocation()).isFalse();
    }

    @Test
    void databaseEnforcesOneProfileCascadeAndScalarJsonEnumConstraints() {
        UserProfile profile = new UserProfile();
        profile.setUser(primaryUser);
        profile.setDesiredRoles(List.of("Engineer"));
        userProfileRepository.saveAndFlush(profile);

        UserProfile duplicate = new UserProfile();
        duplicate.setUser(primaryUser);
        assertThatThrownBy(() -> userProfileRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertProfileConstraintViolation("graduation_year", "1949");
        assertProfileConstraintViolation("graduation_year", "2101");
        assertProfileConstraintViolation("years_experience", "-1");
        assertProfileConstraintViolation("years_experience", "61");
        assertProfileConstraintViolation("desired_roles", "'{}'::jsonb");
        assertProfileConstraintViolation("preferred_locations", "'{}'::jsonb");
        assertProfileConstraintViolation("preferred_work_modes", "'{}'::jsonb");
        assertProfileConstraintViolation("preferred_employment_types", "'{}'::jsonb");
        assertProfileConstraintViolation("education_level", "'UNKNOWN'");
        assertProfileConstraintViolation("preferred_work_modes", "'[\"REMOTE\",\"UNKNOWN\"]'::jsonb");
        assertProfileConstraintViolation("preferred_employment_types", "'[\"FULL_TIME\",\"TEMPORARY\"]'::jsonb");

        UUID profileId = profile.getId();
        userRepository.deleteById(primaryUser.getId());
        userRepository.flush();

        assertThat(userProfileRepository.findById(profileId)).isEmpty();
    }

    @Test
    void servicePersistsEverySupportedEducationLevel() {
        for (EducationLevel educationLevel : EducationLevel.values()) {
            UpdateUserProfileRequest request = fullRequest();
            request.setEducationLevel(educationLevel);

            UserProfileResponse response = userProfileService.updateProfile(primaryUser.getId(), request);

            assertThat(response.educationLevel()).isEqualTo(educationLevel);
            assertThat(userProfileRepository.findByUserId(primaryUser.getId()).orElseThrow().getEducationLevel())
                    .isEqualTo(educationLevel);
        }
    }

    @Test
    void getWithNoProfileReturnsDefaultsAndDoesNotCreateRow() {
        UserProfileResponse response = userProfileService.getProfile(primaryUser);

        assertThat(response.profileId()).isNull();
        assertThat(response.userId()).isEqualTo(primaryUser.getId());
        assertThat(response.email()).isEqualTo(primaryUser.getEmail());
        assertThat(response.firstName()).isEqualTo(primaryUser.getFirstName());
        assertThat(response.lastName()).isEqualTo(primaryUser.getLastName());
        assertThat(response.headline()).isNull();
        assertThat(response.bio()).isNull();
        assertThat(response.city()).isNull();
        assertThat(response.state()).isNull();
        assertThat(response.country()).isNull();
        assertThat(response.educationLevel()).isNull();
        assertThat(response.institution()).isNull();
        assertThat(response.fieldOfStudy()).isNull();
        assertThat(response.graduationYear()).isNull();
        assertThat(response.yearsExperience()).isNull();
        assertThat(response.currentRole()).isNull();
        assertThat(response.desiredRoles()).isEmpty();
        assertThat(response.preferredLocations()).isEmpty();
        assertThat(response.preferredWorkModes()).isEmpty();
        assertThat(response.preferredEmploymentTypes()).isEmpty();
        assertThat(response.openToRelocation()).isFalse();
        assertThat(userProfileRepository.existsByUserId(primaryUser.getId())).isFalse();
    }

    @Test
    void updateCreatesThenUpdatesSameProfileAndProtectsIdentitySecurityFields() {
        String originalEmail = primaryUser.getEmail();
        Role originalRole = primaryUser.getRole();
        String originalPasswordHash = primaryUser.getPasswordHash();
        boolean originalEmailVerified = primaryUser.isEmailVerified();
        boolean originalTotpEnabled = primaryUser.isTotpEnabled();

        UserProfileResponse created = userProfileService.updateProfile(primaryUser.getId(), fullRequest());
        UserProfileResponse updated = userProfileService.updateProfile(primaryUser.getId(), secondRequest());

        assertThat(updated.profileId()).isEqualTo(created.profileId());
        assertThat(userProfileRepository.findByUserId(primaryUser.getId())).isPresent();
        assertThat(userProfileRepository.count()).isOne();
        assertThat(updated.firstName()).isEqualTo("Ada");
        assertThat(updated.lastName()).isEqualTo("Lovelace");
        assertThat(updated.email()).isEqualTo(originalEmail);
        assertThat(updated.headline()).isNull();
        assertThat(updated.city()).isNull();
        assertThat(updated.desiredRoles()).containsExactly("Frontend Engineer");
        assertThat(updated.preferredLocations()).containsExactly("Remote, India");
        assertThat(updated.preferredWorkModes()).containsExactly(WorkMode.ON_SITE);
        assertThat(updated.preferredEmploymentTypes()).containsExactly(EmploymentType.CONTRACT);

        User reloaded = userRepository.findById(primaryUser.getId()).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo(originalEmail);
        assertThat(reloaded.getRole()).isEqualTo(originalRole);
        assertThat(reloaded.getPasswordHash()).isEqualTo(originalPasswordHash);
        assertThat(reloaded.isEmailVerified()).isEqualTo(originalEmailVerified);
        assertThat(reloaded.isTotpEnabled()).isEqualTo(originalTotpEnabled);
    }

    @Test
    void serviceNormalizesCollectionsAndRejectsInvalidCollectionEntries() {
        UpdateUserProfileRequest request = fullRequest();
        request.setDesiredRoles(List.of(" Backend Engineer ", "backend engineer", "Platform Engineer"));
        request.setPreferredLocations(List.of(" Bengaluru ", "bengaluru", "Remote, India"));
        request.setPreferredWorkModes(List.of(WorkMode.HYBRID, WorkMode.REMOTE, WorkMode.HYBRID));
        request.setPreferredEmploymentTypes(List.of(EmploymentType.INTERNSHIP, EmploymentType.FULL_TIME, EmploymentType.INTERNSHIP));

        UserProfileResponse response = userProfileService.updateProfile(primaryUser.getId(), request);

        assertThat(response.desiredRoles()).containsExactly("Backend Engineer", "Platform Engineer");
        assertThat(response.preferredLocations()).containsExactly("Bengaluru", "Remote, India");
        assertThat(response.preferredWorkModes()).containsExactly(WorkMode.HYBRID, WorkMode.REMOTE);
        assertThat(response.preferredEmploymentTypes()).containsExactly(EmploymentType.INTERNSHIP, EmploymentType.FULL_TIME);

        UpdateUserProfileRequest nullEntry = fullRequest();
        nullEntry.setDesiredRoles(new java.util.ArrayList<>(List.of("Engineer")));
        nullEntry.getDesiredRoles().add(null);
        assertThatThrownBy(() -> userProfileService.updateProfile(primaryUser.getId(), nullEntry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot contain null");

        UpdateUserProfileRequest blankEntry = fullRequest();
        blankEntry.setPreferredLocations(List.of("Bengaluru", " "));
        assertThatThrownBy(() -> userProfileService.updateProfile(primaryUser.getId(), blankEntry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot contain blank");

        UpdateUserProfileRequest tooMany = fullRequest();
        tooMany.setDesiredRoles(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"));
        assertThatThrownBy(() -> userProfileService.updateProfile(primaryUser.getId(), tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 10");

        UpdateUserProfileRequest tooLong = fullRequest();
        tooLong.setPreferredLocations(List.of("x".repeat(161)));
        assertThatThrownBy(() -> userProfileService.updateProfile(primaryUser.getId(), tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("160 characters");

        UpdateUserProfileRequest nullWorkMode = fullRequest();
        nullWorkMode.setPreferredWorkModes(new ArrayList<>(List.of(WorkMode.REMOTE)));
        nullWorkMode.getPreferredWorkModes().add(null);
        assertThatThrownBy(() -> userProfileService.updateProfile(primaryUser.getId(), nullWorkMode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Preferred work modes cannot contain null entries");

        UpdateUserProfileRequest nullEmploymentType = fullRequest();
        nullEmploymentType.setPreferredEmploymentTypes(new ArrayList<>(List.of(EmploymentType.FULL_TIME)));
        nullEmploymentType.getPreferredEmploymentTypes().add(null);
        assertThatThrownBy(() -> userProfileService.updateProfile(primaryUser.getId(), nullEmploymentType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Preferred employment types cannot contain null entries");
    }

    @Test
    void serviceRollsBackUserAndProfileTogetherOnNormalizationFailure() {
        UpdateUserProfileRequest invalid = fullRequest();
        invalid.setFirstName("Rollback");
        invalid.setPreferredLocations(List.of(" "));

        assertThatThrownBy(() -> userProfileService.updateProfile(primaryUser.getId(), invalid))
                .isInstanceOf(IllegalArgumentException.class);

        User reloaded = userRepository.findById(primaryUser.getId()).orElseThrow();
        assertThat(reloaded.getFirstName()).isEqualTo("Test");
        assertThat(userProfileRepository.existsByUserId(primaryUser.getId())).isFalse();
    }

    @Test
    void profileControllerSecurityValidationAndAuthMeRefreshWork() throws Exception {
        mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/profile")
                        .cookie(primaryCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profilePayload())))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/profile")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profilePayload())))
                .andExpect(status().isUnauthorized());

        MvcResult result = mockMvc.perform(put("/api/v1/profile")
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profilePayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(primaryUser.getEmail()))
                .andExpect(jsonPath("$.firstName").value("Grace"))
                .andExpect(jsonPath("$.lastName").value("Hopper"))
                .andExpect(jsonPath("$.desiredRoles[0]").value("Backend Engineer"))
                .andExpect(jsonPath("$.preferredLocations[0]").value("Bengaluru"))
                .andExpect(jsonPath("$.preferredWorkModes[0]").value("REMOTE"))
                .andExpect(jsonPath("$.preferredEmploymentTypes[0]").value("INTERNSHIP"))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        assertThat(response).doesNotContain(
                "password",
                "passwordHash",
                "totpSecret",
                "totpSecretEncrypted",
                "session",
                "challenge",
                "token");

        mockMvc.perform(get("/api/v1/auth/me").cookie(primaryCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Grace"))
                .andExpect(jsonPath("$.lastName").value("Hopper"));

        mockMvc.perform(get("/api/v1/profile").cookie(primaryCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Grace"))
                .andExpect(jsonPath("$.email").value(primaryUser.getEmail()));
    }

    @Test
    void controllerReturnsBadRequestForInvalidScalarsCollectionsAndUnknownEnums() throws Exception {
        assertBadPut(Map.of("firstName", "x".repeat(101), "lastName", "User"), "100 characters");
        assertBadPut(Map.of("firstName", "Test", "lastName", "x".repeat(101)), "100 characters");
        assertBadPut(Map.of("firstName", " ", "lastName", "User"), "First name is required");
        assertBadPut(Map.of("firstName", "Test", "lastName", " "), "Last name is required");
        assertBadPut(Map.of("firstName", "Test", "lastName", "User", "headline", "x".repeat(161)), "160 characters");
        assertBadPut(Map.of("firstName", "Test", "lastName", "User", "bio", "x".repeat(2001)), "2000 characters");
        assertBadPut(Map.of("firstName", "Test", "lastName", "User", "city", "x".repeat(101)), "100 characters");
        assertBadPut(Map.of("firstName", "Test", "lastName", "User", "state", "x".repeat(101)), "100 characters");
        assertBadPut(Map.of("firstName", "Test", "lastName", "User", "country", "x".repeat(101)), "100 characters");
        assertBadPut(Map.of("firstName", "Test", "lastName", "User", "institution", "x".repeat(201)), "200 characters");
        assertBadPut(Map.of("firstName", "Test", "lastName", "User", "fieldOfStudy", "x".repeat(201)), "200 characters");
        assertBadPut(Map.of("firstName", "Test", "lastName", "User", "currentRole", "x".repeat(161)), "160 characters");
        assertBadPut(Map.of("firstName", "Test", "lastName", "User", "graduationYear", 1949), "1950 and 2100");
        assertBadPut(Map.of("firstName", "Test", "lastName", "User", "graduationYear", 2101), "1950 and 2100");
        assertBadPut(Map.of("firstName", "Test", "lastName", "User", "yearsExperience", -1), "0 and 60");
        assertBadPut(Map.of("firstName", "Test", "lastName", "User", "yearsExperience", 61), "0 and 60");
        assertBadPut(Map.of("firstName", "Test", "lastName", "User", "desiredRoles", List.of(" ")), "blank");
        assertBadPut(Map.of("firstName", "Test", "lastName", "User", "preferredLocations", List.of(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")), "at most 10");

        assertBadRawPut("{\"firstName\":\"Test\",\"lastName\":\"User\",\"educationLevel\":\"UNKNOWN\"}",
                "Invalid request body");
        assertBadRawPut("{\"firstName\":\"Test\",\"lastName\":\"User\",\"preferredWorkModes\":[\"UNKNOWN\"]}",
                "Invalid request body");
        assertBadRawPut("{\"firstName\":\"Test\",\"lastName\":\"User\",\"preferredEmploymentTypes\":[\"UNKNOWN\"]}",
                "Invalid request body");
        assertBadRawPut("{\"firstName\":\"Test\",\"lastName\":\"User\",\"preferredWorkModes\":[null]}",
                "cannot contain null");
        assertBadRawPut("{\"firstName\":\"Test\",\"lastName\":\"User\",\"preferredEmploymentTypes\":[null]}",
                "cannot contain null");
    }
    @Test
    void usersReceiveOnlyTheirOwnProfile() throws Exception {
        mockMvc.perform(put("/api/v1/profile")
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profilePayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(primaryUser.getEmail()));

        mockMvc.perform(put("/api/v1/profile")
                        .cookie(secondaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Katherine",
                                "lastName", "Johnson",
                                "headline", "Applied math"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(secondaryUser.getEmail()))
                .andExpect(jsonPath("$.headline").value("Applied math"));

        mockMvc.perform(get("/api/v1/profile").cookie(primaryCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(primaryUser.getEmail()))
                .andExpect(jsonPath("$.headline").value("Backend student engineer"));

        mockMvc.perform(get("/api/v1/profile").cookie(secondaryCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(secondaryUser.getEmail()))
                .andExpect(jsonPath("$.headline").value("Applied math"));

        User primary = userRepository.findById(primaryUser.getId()).orElseThrow();
        assertThat(primary.getEmail()).isEqualTo(primaryUser.getEmail());
        assertThat(primary.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void profileUpdateRejectsProtectedAndUnknownFields() throws Exception {
        for (String field : protectedFields()) {
            assertBadRawPut("{\"firstName\":\"Test\",\"lastName\":\"User\",\"" + field + "\":true}",
                    "Invalid request body");
        }

        assertThat(userProfileRepository.existsByUserId(primaryUser.getId())).isFalse();
        User primary = userRepository.findById(primaryUser.getId()).orElseThrow();
        assertThat(primary.getEmail()).isEqualTo(primaryUser.getEmail());
        assertThat(primary.getRole()).isEqualTo(Role.USER);
        assertThat(primary.isEmailVerified()).isTrue();
        assertThat(primary.getEmailVerifiedAt()).isEqualTo(EMAIL_VERIFIED_AT);
        assertThat(primary.isTotpEnabled()).isTrue();
        assertThat(primary.getTotpSecretEncrypted()).isEqualTo("encrypted-secret");
        assertThat(primary.getTotpEnabledAt()).isEqualTo(TOTP_ENABLED_AT);
    }

    @Test
    void accountDeletionRemovesProfileRow() throws Exception {
        UserProfile profile = new UserProfile();
        profile.setUser(primaryUser);
        profile.setHeadline("Delete cascade profile");
        userProfileRepository.saveAndFlush(profile);

        mockMvc.perform(delete("/api/v1/auth/account")
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCOUNT_DELETED"));

        assertThat(userRepository.findById(primaryUser.getId())).isEmpty();
        assertThat(userProfileRepository.findById(profile.getId())).isEmpty();
    }
    private void assertBadPut(Map<String, Object> payload, String expectedMessage) throws Exception {
        mockMvc.perform(put("/api/v1/profile")
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(expectedMessage)));
    }

    private void assertBadRawPut(String json, String expectedMessage) throws Exception {
        mockMvc.perform(put("/api/v1/profile")
                        .cookie(primaryCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(expectedMessage)));
    }
    private void assertProfileConstraintViolation(String column, String sqlValue) {
        UUID id = UUID.randomUUID();
        String graduationYear = "NULL";
        String yearsExperience = "NULL";
        String desiredRoles = "'[]'::jsonb";
        String preferredLocations = "'[]'::jsonb";
        String preferredWorkModes = "'[]'::jsonb";
        String preferredEmploymentTypes = "'[]'::jsonb";
        String educationLevel = "NULL";
        switch (column) {
            case "graduation_year" -> graduationYear = sqlValue;
            case "years_experience" -> yearsExperience = sqlValue;
            case "desired_roles" -> desiredRoles = sqlValue;
            case "preferred_locations" -> preferredLocations = sqlValue;
            case "preferred_work_modes" -> preferredWorkModes = sqlValue;
            case "preferred_employment_types" -> preferredEmploymentTypes = sqlValue;
            case "education_level" -> educationLevel = sqlValue;
            default -> throw new IllegalArgumentException("Unsupported constraint probe: " + column);
        }

        String sql = """
                INSERT INTO user_profiles (
                    id,
                    user_id,
                    graduation_year,
                    years_experience,
                    desired_roles,
                    preferred_locations,
                    preferred_work_modes,
                    preferred_employment_types,
                    education_level,
                    open_to_relocation,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, %s, %s, %s, %s, %s, %s, %s, false, now(), now())
                """.formatted(
                graduationYear,
                yearsExperience,
                desiredRoles,
                preferredLocations,
                preferredWorkModes,
                preferredEmploymentTypes,
                educationLevel);

        assertThatThrownBy(() -> jdbcTemplate.update(sql, id, secondaryUser.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    private UpdateUserProfileRequest fullRequest() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setFirstName(" Grace ");
        request.setLastName(" Hopper ");
        request.setHeadline(" Backend student engineer ");
        request.setBio(" Building useful tools. ");
        request.setCity(" Bengaluru ");
        request.setState(" Karnataka ");
        request.setCountry(" India ");
        request.setEducationLevel(EducationLevel.BACHELORS);
        request.setInstitution(" Arclume University ");
        request.setFieldOfStudy(" Computer Science ");
        request.setGraduationYear(2027);
        request.setYearsExperience(2);
        request.setCurrentRole(" Student Developer ");
        request.setDesiredRoles(List.of(" Backend Engineer ", "Platform Engineer"));
        request.setPreferredLocations(List.of(" Bengaluru ", "Remote, India"));
        request.setPreferredWorkModes(List.of(WorkMode.REMOTE, WorkMode.HYBRID));
        request.setPreferredEmploymentTypes(List.of(EmploymentType.INTERNSHIP, EmploymentType.FULL_TIME));
        request.setOpenToRelocation(true);
        return request;
    }

    private UpdateUserProfileRequest secondRequest() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setHeadline(" ");
        request.setCity(" ");
        request.setDesiredRoles(List.of("Frontend Engineer"));
        request.setPreferredLocations(List.of("Remote, India"));
        request.setPreferredWorkModes(List.of(WorkMode.ON_SITE));
        request.setPreferredEmploymentTypes(List.of(EmploymentType.CONTRACT));
        return request;
    }

    private Map<String, Object> profilePayload() {
        return new java.util.LinkedHashMap<>(Map.ofEntries(
                Map.entry("firstName", "Grace"),
                Map.entry("lastName", "Hopper"),
                Map.entry("headline", "Backend student engineer"),
                Map.entry("bio", "Building useful tools."),
                Map.entry("city", "Bengaluru"),
                Map.entry("state", "Karnataka"),
                Map.entry("country", "India"),
                Map.entry("educationLevel", "BACHELORS"),
                Map.entry("institution", "Arclume University"),
                Map.entry("fieldOfStudy", "Computer Science"),
                Map.entry("graduationYear", 2027),
                Map.entry("yearsExperience", 2),
                Map.entry("currentRole", "Student Developer"),
                Map.entry("desiredRoles", List.of("Backend Engineer", "backend engineer", "Platform Engineer")),
                Map.entry("preferredLocations", List.of("Bengaluru", "bengaluru", "Remote, India")),
                Map.entry("preferredWorkModes", List.of("REMOTE", "HYBRID", "REMOTE")),
                Map.entry("preferredEmploymentTypes", List.of("INTERNSHIP", "FULL_TIME", "INTERNSHIP")),
                Map.entry("openToRelocation", true)
        ));
    }

    private List<String> protectedFields() {
        return List.of(
                "userId",
                "profileId",
                "email",
                "role",
                "password",
                "passwordHash",
                "emailVerified",
                "emailVerifiedAt",
                "totpEnabled",
                "totpSecretEncrypted",
                "totpEnabledAt",
                "createdAt",
                "updatedAt",
                "sessionToken",
                "challengeToken",
                "unknownField"
        );
    }
    private User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(EMAIL_VERIFIED_AT);
        user.setTotpEnabled(true);
        user.setTotpSecretEncrypted("encrypted-secret");
        user.setTotpEnabledAt(TOTP_ENABLED_AT);
        return userRepository.saveAndFlush(user);
    }

    private Cookie authCookie(User user) {
        return new Cookie(AuthCookieService.SESSION_COOKIE, sessionService.create(user, "integration-test", "127.0.0.1").token());
    }
}

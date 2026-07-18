package com.arclume.api.job;

import com.arclume.api.client.JobicyJobClient;
import com.arclume.api.client.RemotiveJobClient;
import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.OpportunitySyncStatus;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.User;
import com.arclume.api.repository.JobRepository;
import com.arclume.api.repository.OpportunitySyncRunRepository;
import com.arclume.api.repository.UserRepository;
import com.arclume.api.security.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpportunitySyncDisabledIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private OpportunitySyncRunRepository opportunitySyncRunRepository;

    @Autowired
    private SessionService sessionService;

    @MockitoBean
    private RemotiveJobClient remotiveJobClient;

    @MockitoBean
    private JobicyJobClient jobicyJobClient;

    @BeforeEach
    void setUp() {
        opportunitySyncRunRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void unauthenticatedUsersCannotTriggerProviderSync() throws Exception {
        mockMvc.perform(post("/api/v1/admin/opportunity-sync/remotive").with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(remotiveJobClient);
    }

    @Test
    void nonAdminUsersCannotTriggerProviderSync() throws Exception {
        Cookie userCookie = createCookie("provider-sync-user@example.com", Role.USER);

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/remotive")
                        .cookie(userCookie)
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(remotiveJobClient);
    }

    @Test
    void disabledProviderReturnsSkippedRunWithoutHttpCall() throws Exception {
        Cookie adminCookie = createCookie("provider-sync-admin@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/remotive")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.providerKey").value("REMOTIVE"))
                .andExpect(jsonPath("$.status").value("SKIPPED"))
                .andExpect(jsonPath("$.recordsFetched").value(0))
                .andExpect(jsonPath("$.syncError").value("Provider REMOTIVE is disabled"));

        verifyNoInteractions(remotiveJobClient);
        assertThat(opportunitySyncRunRepository.findAll())
                .singleElement()
                .satisfies(run -> {
                    assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.SKIPPED);
                    assertThat(run.getFinishedAt()).isNotNull();
                });
    }

    @Test
    void disabledJobicyProviderReturnsSkippedRunWithoutHttpCall() throws Exception {
        Cookie adminCookie = createCookie("jobicy-disabled-admin@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/JOBICY")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.providerKey").value("JOBICY"))
                .andExpect(jsonPath("$.status").value("SKIPPED"))
                .andExpect(jsonPath("$.recordsFetched").value(0))
                .andExpect(jsonPath("$.syncError").value("Provider JOBICY is disabled"));

        verifyNoInteractions(jobicyJobClient);
        assertThat(opportunitySyncRunRepository.findAll())
                .singleElement()
                .satisfies(run -> {
                    assertThat(run.getProviderKey()).isEqualTo("JOBICY");
                    assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.SKIPPED);
                    assertThat(run.getFinishedAt()).isNotNull();
                });
    }

    @Test
    void unauthenticatedUsersCannotTriggerLegacySync() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/sync").with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(remotiveJobClient);
    }

    @Test
    void nonAdminUsersCannotTriggerLegacySync() throws Exception {
        Cookie userCookie = createCookie("legacy-sync-user@example.com", Role.USER);

        mockMvc.perform(post("/api/v1/jobs/sync")
                        .cookie(userCookie)
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(remotiveJobClient);
    }

    @Test
    void legacySyncRequiresCsrfForAdmin() throws Exception {
        Cookie adminCookie = createCookie("legacy-sync-missing-csrf@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/v1/jobs/sync")
                        .cookie(adminCookie))
                .andExpect(status().isForbidden());
        verifyNoInteractions(remotiveJobClient);
    }

    @Test
    void disabledProviderReturnsConflictFromLegacySyncWithoutHttpCall() throws Exception {
        Cookie adminCookie = createCookie("legacy-sync-admin@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/v1/jobs/sync")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fetchedCount").value(0))
                .andExpect(jsonPath("$.insertedCount").value(0))
                .andExpect(jsonPath("$.updatedCount").value(0))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.failedCount").value(0));

        verifyNoInteractions(remotiveJobClient);
        assertThat(opportunitySyncRunRepository.findAll())
                .singleElement()
                .satisfies(run -> assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.SKIPPED));
    }

    private Cookie createCookie(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPasswordHash("hashed");
        user.setRole(role);
        user = userRepository.saveAndFlush(user);
        String token = sessionService.create(user, "integration-test", "127.0.0.1").token();
        return new Cookie("ARCLUME_SESSION", token);
    }
}

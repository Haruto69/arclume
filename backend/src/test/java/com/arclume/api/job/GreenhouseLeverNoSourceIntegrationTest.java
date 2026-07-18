package com.arclume.api.job;

import com.arclume.api.client.GreenhouseJobClient;
import com.arclume.api.client.JobicyJobClient;
import com.arclume.api.client.LeverJobClient;
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

@SpringBootTest(properties = {
        "app.opportunity.providers.greenhouse.enabled=true",
        "app.opportunity.providers.lever.enabled=true"
})
@AutoConfigureMockMvc
class GreenhouseLeverNoSourceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private OpportunitySyncRunRepository opportunitySyncRunRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionService sessionService;

    @MockitoBean
    private GreenhouseJobClient greenhouseJobClient;

    @MockitoBean
    private LeverJobClient leverJobClient;

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
    void enabledGreenhouseWithNoEnabledSourcesProducesFailedRunWithoutHttp() throws Exception {
        Cookie adminCookie = createCookie("greenhouse-no-source-admin@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/GREENHOUSE")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.providerKey").value("GREENHOUSE"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.recordsFetched").value(0))
                .andExpect(jsonPath("$.recordsFailed").value(1))
                .andExpect(jsonPath("$.syncError").value("Provider sync failed: OpportunityProviderException"));

        verifyNoInteractions(greenhouseJobClient);
        assertThat(opportunitySyncRunRepository.findAll()).singleElement().satisfies(run -> {
            assertThat(run.getProviderKey()).isEqualTo("GREENHOUSE");
            assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.FAILED);
            assertThat(run.getSyncError()).isEqualTo("Provider sync failed: OpportunityProviderException");
        });
    }

    @Test
    void enabledLeverWithNoEnabledSourcesProducesFailedRunWithoutHttp() throws Exception {
        Cookie adminCookie = createCookie("lever-no-source-admin@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/v1/admin/opportunity-sync/LEVER")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.providerKey").value("LEVER"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.recordsFetched").value(0))
                .andExpect(jsonPath("$.recordsFailed").value(1))
                .andExpect(jsonPath("$.syncError").value("Provider sync failed: OpportunityProviderException"));

        verifyNoInteractions(leverJobClient);
        assertThat(opportunitySyncRunRepository.findAll()).singleElement().satisfies(run -> {
            assertThat(run.getProviderKey()).isEqualTo("LEVER");
            assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.FAILED);
            assertThat(run.getSyncError()).isEqualTo("Provider sync failed: OpportunityProviderException");
        });
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

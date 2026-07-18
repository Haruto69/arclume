package com.arclume.api.job;

import com.arclume.api.client.TheMuseJobClient;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.opportunity.providers.the-muse.enabled=true",
        "app.opportunity.providers.the-muse.api-key= "
})
@AutoConfigureMockMvc
class TheMuseMissingKeyOpportunitySyncIntegrationTest extends BaseIntegrationTest {

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
    private TheMuseJobClient theMuseJobClient;

    @BeforeEach
    void setUp() {
        reset(theMuseJobClient);
        opportunitySyncRunRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void enabledTheMuseProviderWithoutApiKeyFailsSafelyBeforeHttp() throws Exception {
        Cookie adminCookie = createCookie("muse-missing-key@example.com", Role.ADMIN);

        MvcResult result = mockMvc.perform(post("/api/v1/admin/opportunity-sync/THE_MUSE")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.providerKey").value("THE_MUSE"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.recordsFetched").value(0))
                .andExpect(jsonPath("$.recordsFailed").value(1))
                .andExpect(jsonPath("$.syncError").value("Provider sync failed: OpportunityProviderException"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("api-key", "api_key", "THE_MUSE_API_KEY");
        verifyNoInteractions(theMuseJobClient);
        assertThat(jobRepository.findAll()).isEmpty();
        assertThat(opportunitySyncRunRepository.findAll()).singleElement().satisfies(run -> {
            assertThat(run.getProviderKey()).isEqualTo("THE_MUSE");
            assertThat(run.getStatus()).isEqualTo(OpportunitySyncStatus.FAILED);
            assertThat(run.getSyncError()).isEqualTo("Provider sync failed: OpportunityProviderException");
            assertThat(run.getRecordsDeactivated()).isZero();
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

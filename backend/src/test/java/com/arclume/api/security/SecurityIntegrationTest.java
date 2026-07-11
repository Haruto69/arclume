package com.arclume.api.security;

import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.User;
import com.arclume.api.dto.LoginRequest;
import com.arclume.api.dto.RegisterRequest;
import com.arclume.api.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
class SecurityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void healthAndActuatorArePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void csrfInitializationEndpointSetsCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void unsafeRequestsWithoutCsrfTokenAreForbidden() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("csrf@example.com");
        request.setPassword("password123");
        request.setFirstName("No");
        request.setLastName("CSRF");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void validCsrfRequestsSucceedAndHashPassword() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("register@example.com");
        request.setPassword("securePassword123");
        request.setFirstName("John");
        request.setLastName("Doe");

        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmailIgnoreCase("register@example.com").orElse(null);
        assertThat(user).isNotNull();
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(passwordEncoder.matches("securePassword123", user.getPasswordHash())).isTrue();
    }

    @Test
    void duplicateEmailReturnsConflictStatus() throws Exception {
        User existingUser = new User();
        existingUser.setEmail("duplicate@example.com");
        existingUser.setFirstName("Alice");
        existingUser.setLastName("Smith");
        existingUser.setPasswordHash(passwordEncoder.encode("password123"));
        userRepository.saveAndFlush(existingUser);

        RegisterRequest request = new RegisterRequest();
        request.setEmail("duplicate@example.com");
        request.setPassword("password456");
        request.setFirstName("Bob");
        request.setLastName("Jones");

        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void validLoginSetsJwtCookieAndInvalidLoginFailsSafely() throws Exception {
        User user = new User();
        user.setEmail("login@example.com");
        user.setFirstName("Alice");
        user.setLastName("Smith");
        user.setPasswordHash(passwordEncoder.encode("correctPassword"));
        userRepository.saveAndFlush(user);

        // 1. Invalid Login
        LoginRequest invalidLogin = new LoginRequest();
        invalidLogin.setEmail("login@example.com");
        invalidLogin.setPassword("wrongPassword");

        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLogin)))
                .andExpect(status().isUnauthorized());

        // 2. Valid Login
        LoginRequest validLogin = new LoginRequest();
        validLogin.setEmail("login@example.com");
        validLogin.setPassword("correctPassword");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLogin)))
                .andExpect(status().isOk())
                .andReturn();

        String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookieHeader).contains("ARCLUME_ACCESS_TOKEN");
        assertThat(setCookieHeader).contains("HttpOnly");
    }

    @Test
    void authenticatedUserMeEndpointRejectsMissingOrInvalidJwtAndAcceptsValidJwt() throws Exception {
        User user = new User();
        user.setEmail("me@example.com");
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setPasswordHash(passwordEncoder.encode("password"));
        user = userRepository.saveAndFlush(user);

        // 1. Reject without JWT cookie
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        // 2. Reject with invalid JWT cookie
        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new Cookie("ARCLUME_ACCESS_TOKEN", "invalid.jwt.token")))
                .andExpect(status().isUnauthorized());

        // 3. Authenticate with valid login token
        LoginRequest login = new LoginRequest();
        login.setEmail("me@example.com");
        login.setPassword("password");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie authCookie = loginResult.getResponse().getCookie("ARCLUME_ACCESS_TOKEN");
        assertThat(authCookie).isNotNull();

        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(authCookie))
                .andExpect(status().isOk());
    }

    @Test
    void logoutClearsJwtCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout").with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        Cookie clearedCookie = result.getResponse().getCookie("ARCLUME_ACCESS_TOKEN");
        assertThat(clearedCookie).isNotNull();
        assertThat(clearedCookie.getMaxAge()).isEqualTo(0);
    }
}

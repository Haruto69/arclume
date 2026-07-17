package com.arclume.api.security;

import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.EmailVerificationToken;
import com.arclume.api.domain.RecoveryCode;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.User;
import com.arclume.api.domain.UserSession;
import com.arclume.api.dto.LoginRequest;
import com.arclume.api.dto.RegisterRequest;
import com.arclume.api.repository.EmailVerificationTokenRepository;
import com.arclume.api.repository.RecoveryCodeRepository;
import com.arclume.api.repository.UserRepository;
import com.arclume.api.repository.UserSessionRepository;
import com.arclume.api.service.EmailVerificationService;
import com.arclume.api.service.RecoveryCodeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class SecurityIntegrationTest extends BaseIntegrationTest {

    private static final String TEST_CHALLENGE_SECRET =
            "secure_challenge_secret_at_least_32_bytes_long_for_test";

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private UserRepository userRepository;
    @Autowired private EmailVerificationTokenRepository verificationTokenRepository;
    @Autowired private UserSessionRepository userSessionRepository;
    @Autowired private RecoveryCodeRepository recoveryCodeRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmailVerificationService emailVerificationService;
    @Autowired private RecoveryCodeService recoveryCodeService;
    @Autowired private SessionService sessionService;
    @Autowired private TotpService totpService;
    @Autowired private SecretEncryptionService encryptionService;
    @Autowired private TokenHashService tokenHashService;

    @BeforeEach
    void setUp() {
        userSessionRepository.deleteAll();
        recoveryCodeRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void publicHealthAndCsrfBootstrapRemainAvailable() throws Exception {
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void unsafeRegistrationWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("csrf@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationCreatesUnverifiedUserAndVerificationTokenWithoutSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("register@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("VERIFICATION_REQUIRED"))
                .andReturn();

        assertThat(result.getResponse().getCookie(AuthCookieService.SESSION_COOKIE)).isNull();
        User user = userRepository.findByEmailIgnoreCase("register@example.com").orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.isTotpEnabled()).isFalse();
        assertThat(passwordEncoder.matches("securePassword123", user.getPasswordHash())).isTrue();

        List<EmailVerificationToken> tokens = verificationTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.getFirst().getTokenHash()).doesNotContain("register@example.com");
    }

    @Test
    void duplicateRegistrationRetainsExistingConflictBehavior() throws Exception {
        createUser("duplicate@example.com", false, null);
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("duplicate@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void loginBeforeEmailVerificationReturnsTypedStatusAndNoSession() throws Exception {
        createUser("unverified@example.com", false, null);
        MvcResult result = passwordLogin("unverified@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EMAIL_NOT_VERIFIED"))
                .andReturn();

        assertThat(result.getResponse().getCookie(AuthCookieService.SESSION_COOKIE)).isNull();
        assertThat(result.getResponse().getCookie(AuthCookieService.CHALLENGE_COOKIE).getMaxAge()).isZero();
    }

    @Test
    void validVerificationSucceedsAndInvalidOrExpiredTokensFailCleanly() throws Exception {
        User user = createUser("verify@example.com", false, null);
        String rawToken = emailVerificationService.issueToken(user);

        mockMvc.perform(get("/api/v1/auth/verify-email").queryParam("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EMAIL_VERIFIED"));

        User verified = userRepository.findById(user.getId()).orElseThrow();
        assertThat(verified.isEmailVerified()).isTrue();
        assertThat(verified.getEmailVerifiedAt()).isNotNull();

        EmailVerificationToken consumed = verificationTokenRepository
                .findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow();
        assertThat(consumed.getConsumedAt()).isNotNull();
        assertThat(consumed.getTokenHash()).isNotEqualTo(rawToken);

        mockMvc.perform(get("/api/v1/auth/verify-email").queryParam("token", rawToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_INVALID"));

        mockMvc.perform(get("/api/v1/auth/verify-email").queryParam("token", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_INVALID"));

        User expiredUser = createUser("expired@example.com", false, null);
        EmailVerificationToken expired = new EmailVerificationToken();
        expired.setUser(expiredUser);
        expired.setTokenHash(tokenHashService.hash("expired-token"));
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        verificationTokenRepository.saveAndFlush(expired);

        mockMvc.perform(get("/api/v1/auth/verify-email").queryParam("token", "expired-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_INVALID"));
    }

    @Test
    void resendVerificationIsGenericAndHonorsCooldown() throws Exception {
        createUser("resend@example.com", false, null);

        mockMvc.perform(post("/api/v1/auth/resend-verification").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "resend@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFICATION_SENT"));
        assertThat(verificationTokenRepository.findAll()).hasSize(1);

        mockMvc.perform(post("/api/v1/auth/resend-verification").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "resend@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFICATION_SENT"));
        assertThat(verificationTokenRepository.findAll()).hasSize(1);

        mockMvc.perform(post("/api/v1/auth/resend-verification").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "missing@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFICATION_SENT"));
    }

    @Test
    void verifiedPasswordLoginRequiresTotpSetupAndRejectsMissingTamperedOrExpiredChallenges() throws Exception {
        User user = createUser("setup-required@example.com", true, null);

        MvcResult login = passwordLogin("setup-required@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TOTP_SETUP_REQUIRED"))
                .andReturn();
        Cookie challenge = login.getResponse().getCookie(AuthCookieService.CHALLENGE_COOKIE);
        assertThat(challenge).isNotNull();
        assertThat(challenge.isHttpOnly()).isTrue();
        assertThat(challenge.getMaxAge()).isEqualTo(10 * 60);
        assertThat(login.getResponse().getCookie(AuthCookieService.SESSION_COOKIE)).isNull();

        mockMvc.perform(post("/api/v1/auth/totp/setup/start").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_CHALLENGE_INVALID"));

        String value = challenge.getValue();
        int signatureStart = value.lastIndexOf('.') + 1;
        char signatureCharacter = value.charAt(signatureStart);
        String tamperedValue = value.substring(0, signatureStart)
                + (signatureCharacter == 'A' ? 'B' : 'A')
                + value.substring(signatureStart + 1);
        mockMvc.perform(post("/api/v1/auth/totp/setup/start").with(csrf())
                        .cookie(new Cookie(AuthCookieService.CHALLENGE_COOKIE, tamperedValue)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_CHALLENGE_INVALID"));

        mockMvc.perform(post("/api/v1/auth/totp/setup/start").with(csrf())
                        .cookie(expiredChallenge(user, PendingLoginService.Stage.TOTP_SETUP_REQUIRED)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_CHALLENGE_INVALID"));
    }

    @Test
    void totpSetupEnablesTotpHashesRecoveryCodesAndIssuesPersistentSession() throws Exception {
        User user = createUser("setup@example.com", true, null);
        Cookie challenge = passwordChallenge("setup@example.com");

        MvcResult start = mockMvc.perform(post("/api/v1/auth/totp/setup/start").with(csrf())
                        .cookie(challenge))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.startsWith(
                        "otpauth://totp/Arclume%3Asetup%40example.com")))
                .andReturn();

        User pendingSetup = userRepository.findById(user.getId()).orElseThrow();
        assertThat(pendingSetup.isTotpEnabled()).isFalse();
        assertThat(pendingSetup.getTotpSecretEncrypted()).isNotBlank();
        assertThat(start.getResponse().getCookie(AuthCookieService.SESSION_COOKIE)).isNull();

        JsonNode setup = objectMapper.readTree(start.getResponse().getContentAsString());
        String secret = setup.get("secret").asText();
        String code = totpService.currentCode(secret);

        MvcResult confirm = mockMvc.perform(post("/api/v1/auth/totp/setup/confirm").with(csrf())
                        .cookie(challenge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeJson(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10))
                .andReturn();

        Cookie sessionCookie = confirm.getResponse().getCookie(AuthCookieService.SESSION_COOKIE);
        assertThat(sessionCookie).isNotNull();
        assertThat(sessionCookie.isHttpOnly()).isTrue();
        assertThat(sessionCookie.getMaxAge()).isEqualTo(30 * 24 * 60 * 60);
        assertThat(confirm.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(value -> value.contains("ARCLUME_SESSION=")
                        && value.contains("HttpOnly")
                        && value.contains("SameSite=Strict"));
        assertThat(confirm.getResponse().getCookie(AuthCookieService.CHALLENGE_COOKIE).getMaxAge()).isZero();

        User configured = userRepository.findById(user.getId()).orElseThrow();
        assertThat(configured.isTotpEnabled()).isTrue();
        assertThat(configured.getTotpEnabledAt()).isNotNull();
        assertThat(configured.getTotpSecretEncrypted()).doesNotContain(secret);

        List<String> rawCodes = new ArrayList<>();
        objectMapper.readTree(confirm.getResponse().getContentAsString()).get("recoveryCodes")
                .forEach(node -> rawCodes.add(node.asText()));
        List<RecoveryCode> storedCodes = recoveryCodeRepository.findAllByUserId(user.getId());
        assertThat(storedCodes).hasSize(10);
        assertThat(storedCodes).allSatisfy(stored ->
                assertThat(rawCodes).doesNotContain(stored.getCodeHash()));
    }

    @Test
    void enabledAccountRequiresTotpAndValidTotpRestoresSession() throws Exception {
        String secret = totpService.generateSecret();
        createUser("totp@example.com", true, secret);
        MvcResult passwordStep = passwordLogin("totp@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TOTP_REQUIRED"))
                .andReturn();
        Cookie challenge = passwordStep.getResponse().getCookie(AuthCookieService.CHALLENGE_COOKIE);
        assertThat(challenge).isNotNull();
        String currentCode = totpService.currentCode(secret);
        String invalidCode = "000000".equals(currentCode) ? "000001" : "000000";

        mockMvc.perform(post("/api/v1/auth/login/totp").with(csrf())
                        .cookie(challenge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeJson(invalidCode)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOTP_INVALID"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/totp").with(csrf())
                        .cookie(challenge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeJson(currentCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andReturn();

        Cookie session = result.getResponse().getCookie(AuthCookieService.SESSION_COOKIE);
        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("totp@example.com"));

        mockMvc.perform(post("/api/v1/auth/login/totp").with(csrf())
                        .cookie(challenge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeJson(currentCode)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_CHALLENGE_INVALID"));
    }

    @Test
    void totpAttemptsAreLimitedPerPasswordChallenge() throws Exception {
        String secret = totpService.generateSecret();
        createUser("limited@example.com", true, secret);
        Cookie challenge = passwordChallenge("limited@example.com");
        String currentCode = totpService.currentCode(secret);
        String invalidCode = "000000".equals(currentCode) ? "000001" : "000000";

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login/totp").with(csrf())
                            .cookie(challenge)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(codeJson(invalidCode)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOTP_INVALID"));
        }

        mockMvc.perform(post("/api/v1/auth/login/totp").with(csrf())
                        .cookie(challenge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeJson(invalidCode)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void recoveryCodeAuthenticatesOnceAndReuseFails() throws Exception {
        String secret = totpService.generateSecret();
        User user = createUser("recovery@example.com", true, secret);
        String recoveryCode = recoveryCodeService.replaceCodes(user).getFirst();

        Cookie firstChallenge = passwordChallenge("recovery@example.com");
        mockMvc.perform(post("/api/v1/auth/recovery-code/login").with(csrf())
                        .cookie(firstChallenge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeJson(recoveryCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"));

        Cookie secondChallenge = passwordChallenge("recovery@example.com");
        mockMvc.perform(post("/api/v1/auth/recovery-code/login").with(csrf())
                        .cookie(secondChallenge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeJson(recoveryCode)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("RECOVERY_CODE_INVALID"));
    }

    @Test
    void meRejectsMissingAndInvalidSessionsButAcceptsStoredSession() throws Exception {
        User user = createUser("me@example.com", true, totpService.generateSecret());

        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new Cookie(AuthCookieService.SESSION_COOKIE, "invalid")))
                .andExpect(status().isUnauthorized());

        SessionService.IssuedSession session = sessionService.create(user, "test", "127.0.0.1");
        UserSession storedSession = sessionRecord(session.token());
        Instant previousUse = Instant.now().minusSeconds(60);
        storedSession.setLastUsedAt(previousUse);
        userSessionRepository.saveAndFlush(storedSession);

        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new Cookie(AuthCookieService.SESSION_COOKIE, session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.totpEnabled").value(true));

        UserSession updatedSession = sessionRecord(session.token());
        assertThat(updatedSession.getLastUsedAt()).isAfter(previousUse);
    }

    @Test
    void expiredAndRevokedSessionsAreRejected() throws Exception {
        User user = createUser("session-lifecycle@example.com", true, totpService.generateSecret());

        SessionService.IssuedSession expired = sessionService.create(user, "expired", "127.0.0.1");
        UserSession expiredRecord = sessionRecord(expired.token());
        expiredRecord.setExpiresAt(Instant.now().minusSeconds(1));
        userSessionRepository.saveAndFlush(expiredRecord);

        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new Cookie(AuthCookieService.SESSION_COOKIE, expired.token())))
                .andExpect(status().isUnauthorized());

        SessionService.IssuedSession revoked = sessionService.create(user, "revoked", "127.0.0.1");
        UserSession revokedRecord = sessionRecord(revoked.token());
        revokedRecord.setRevokedAt(Instant.now());
        userSessionRepository.saveAndFlush(revokedRecord);

        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new Cookie(AuthCookieService.SESSION_COOKIE, revoked.token())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesCurrentSessionAndLogoutAllRevokesEverySession() throws Exception {
        User user = createUser("logout@example.com", true, totpService.generateSecret());
        SessionService.IssuedSession first = sessionService.create(user, "test-1", "127.0.0.1");
        SessionService.IssuedSession second = sessionService.create(user, "test-2", "127.0.0.1");

        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout").with(csrf())
                        .cookie(new Cookie(AuthCookieService.SESSION_COOKIE, first.token())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(logout.getResponse().getCookie(AuthCookieService.SESSION_COOKIE).getMaxAge()).isZero();

        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new Cookie(AuthCookieService.SESSION_COOKIE, first.token())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout-all").with(csrf())
                        .cookie(new Cookie(AuthCookieService.SESSION_COOKIE, second.token())))
                .andExpect(status().isOk());

        assertThat(userSessionRepository.findAll())
                .extracting(UserSession::getRevokedAt)
                .doesNotContainNull();
    }

    @Test
    void protectedApiRejectsAnonymousBasicAndBearerAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isUnauthorized());

        String basicCredentials = Base64.getEncoder()
                .encodeToString("user@example.com:password".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get("/api/v1/jobs")
                        .header("Authorization", "Basic " + basicCredentials))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/jobs")
                        .header("Authorization", "Bearer legacy-token"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions passwordLogin(String email) throws Exception {
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("securePassword123");
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)));
    }

    private Cookie passwordChallenge(String email) throws Exception {
        MvcResult result = passwordLogin(email)
                .andExpect(status().isOk())
                .andReturn();
        Cookie challenge = result.getResponse().getCookie(AuthCookieService.CHALLENGE_COOKIE);
        assertThat(challenge).isNotNull();
        return challenge;
    }

    private UserSession sessionRecord(String token) {
        String tokenHash = tokenHashService.hash(token);
        return userSessionRepository.findAll().stream()
                .filter(session -> session.getSessionTokenHash().equals(tokenHash))
                .findFirst()
                .orElseThrow();
    }
    private Cookie expiredChallenge(User user, PendingLoginService.Stage stage) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .claim("stage", stage.name())
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(TEST_CHALLENGE_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return new Cookie(AuthCookieService.CHALLENGE_COOKIE, token);
    }

    private User createUser(String email, boolean verified, String totpSecret) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPasswordHash(passwordEncoder.encode("securePassword123"));
        user.setRole(Role.USER);
        user.setEmailVerified(verified);
        if (verified) {
            user.setEmailVerifiedAt(Instant.now());
        }
        if (totpSecret != null) {
            user.setTotpEnabled(true);
            user.setTotpEnabledAt(Instant.now());
            user.setTotpSecretEncrypted(encryptionService.encrypt(totpSecret));
        }
        return userRepository.saveAndFlush(user);
    }

    private String registerJson(String email) throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword("securePassword123");
        request.setFirstName("Test");
        request.setLastName("User");
        return objectMapper.writeValueAsString(request);
    }

    private String codeJson(String code) throws Exception {
        return objectMapper.writeValueAsString(Map.of("code", code));
    }
}
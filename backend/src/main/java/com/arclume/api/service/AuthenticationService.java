package com.arclume.api.service;

import com.arclume.api.domain.Role;
import com.arclume.api.domain.User;
import com.arclume.api.dto.AuthStatus;
import com.arclume.api.dto.LoginRequest;
import com.arclume.api.dto.LoginResponse;
import com.arclume.api.dto.RegisterRequest;
import com.arclume.api.dto.RegistrationResponse;
import com.arclume.api.dto.TotpConfirmResponse;
import com.arclume.api.dto.TotpSetupResponse;
import com.arclume.api.dto.UserResponse;
import com.arclume.api.repository.UserRepository;
import com.arclume.api.security.AuthAttemptLimiter;
import com.arclume.api.security.AuthException;
import com.arclume.api.security.PendingLoginService;
import com.arclume.api.security.SecretEncryptionService;
import com.arclume.api.security.SessionService;
import com.arclume.api.security.TotpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthenticationService {

    public record PasswordStep(LoginResponse response, String challengeToken) {}
    public record LoginCompletion(LoginResponse response, String sessionToken) {}
    public record SetupCompletion(TotpConfirmResponse response, String sessionToken) {}

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final PendingLoginService pendingLoginService;
    private final SecretEncryptionService encryptionService;
    private final TotpService totpService;
    private final RecoveryCodeService recoveryCodeService;
    private final SessionService sessionService;
    private final AuthAttemptLimiter attemptLimiter;

    public AuthenticationService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 EmailVerificationService emailVerificationService,
                                 PendingLoginService pendingLoginService,
                                 SecretEncryptionService encryptionService,
                                 TotpService totpService,
                                 RecoveryCodeService recoveryCodeService,
                                 SessionService sessionService,
                                 AuthAttemptLimiter attemptLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.pendingLoginService = pendingLoginService;
        this.encryptionService = encryptionService;
        this.totpService = totpService;
        this.recoveryCodeService = recoveryCodeService;
        this.sessionService = sessionService;
        this.attemptLimiter = attemptLimiter;
    }

    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                    "Email is already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        user.setEmailVerified(false);
        user.setTotpEnabled(false);
        userRepository.save(user);
        emailVerificationService.issueAndSend(user);
        return new RegistrationResponse(AuthStatus.VERIFICATION_REQUIRED,
                "Check your email to validate your account.");
    }

    @Transactional(readOnly = true)
    public PasswordStep passwordLogin(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .orElse(null);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Invalid email or password");
        }
        if (!user.isEmailVerified()) {
            return new PasswordStep(new LoginResponse(AuthStatus.EMAIL_NOT_VERIFIED,
                    "Validate your account before signing in.", null), null);
        }
        PendingLoginService.Stage stage = user.isTotpEnabled()
                ? PendingLoginService.Stage.TOTP_REQUIRED
                : PendingLoginService.Stage.TOTP_SETUP_REQUIRED;
        String challenge = pendingLoginService.create(user.getId(), stage);
        return new PasswordStep(new LoginResponse(AuthStatus.valueOf(stage.name()),
                user.isTotpEnabled()
                        ? "Enter the 6-digit code from your authenticator app."
                        : "Set up an authenticator app to finish signing in.",
                null), challenge);
    }

    @Transactional
    public TotpSetupResponse startTotpSetup(String challengeToken) {
        PendingLoginService.Challenge challenge = pendingLoginService.validate(
                challengeToken, PendingLoginService.Stage.TOTP_SETUP_REQUIRED);
        User user = verifiedUserForUpdate(challenge.userId());
        pendingLoginService.ensureActive(challenge);
        if (user.isTotpEnabled()) {
            throw challengeInvalid();
        }
        String secret = totpService.generateSecret();
        user.setTotpSecretEncrypted(encryptionService.encrypt(secret));
        return new TotpSetupResponse(totpService.buildOtpAuthUri(user.getEmail(), secret), secret);
    }

    @Transactional
    public SetupCompletion confirmTotpSetup(String challengeToken, String code, HttpServletRequest request) {
        PendingLoginService.Challenge challenge = pendingLoginService.validate(
                challengeToken, PendingLoginService.Stage.TOTP_SETUP_REQUIRED);
        attemptLimiter.checkAllowed(challenge.challengeId());
        User user = verifiedUserForUpdate(challenge.userId());
        pendingLoginService.ensureActive(challenge);
        if (user.isTotpEnabled()) {
            throw challengeInvalid();
        }
        String secret = decryptPendingSecret(user);
        if (!totpService.verify(secret, code)) {
            attemptLimiter.recordFailure(challenge.challengeId());
            throw invalidTotp();
        }
        Instant now = Instant.now();
        user.setTotpEnabled(true);
        user.setTotpEnabledAt(now);
        List<String> recoveryCodes = recoveryCodeService.replaceCodes(user);
        SessionService.IssuedSession session = sessionService.create(user, request);
        attemptLimiter.clear(challenge.challengeId());
        pendingLoginService.consume(challenge);
        TotpConfirmResponse response = new TotpConfirmResponse(AuthStatus.AUTHENTICATED,
                "Authenticator setup complete. Save your recovery codes.", toResponse(user), recoveryCodes);
        return new SetupCompletion(response, session.token());
    }

    @Transactional
    public LoginCompletion completeTotpLogin(String challengeToken, String code, HttpServletRequest request) {
        PendingLoginService.Challenge challenge = pendingLoginService.validate(
                challengeToken, PendingLoginService.Stage.TOTP_REQUIRED);
        attemptLimiter.checkAllowed(challenge.challengeId());
        User user = verifiedUserForUpdate(challenge.userId());
        pendingLoginService.ensureActive(challenge);
        if (!user.isTotpEnabled() || !totpService.verify(decryptPendingSecret(user), code)) {
            attemptLimiter.recordFailure(challenge.challengeId());
            throw invalidTotp();
        }
        SessionService.IssuedSession session = sessionService.create(user, request);
        attemptLimiter.clear(challenge.challengeId());
        pendingLoginService.consume(challenge);
        return new LoginCompletion(authenticated(user), session.token());
    }

    @Transactional
    public LoginCompletion completeRecoveryLogin(String challengeToken, String code, HttpServletRequest request) {
        PendingLoginService.Challenge challenge = pendingLoginService.validate(
                challengeToken, PendingLoginService.Stage.TOTP_REQUIRED);
        attemptLimiter.checkAllowed(challenge.challengeId());
        User user = verifiedUserForUpdate(challenge.userId());
        pendingLoginService.ensureActive(challenge);
        if (!user.isTotpEnabled() || !recoveryCodeService.consume(user, code)) {
            attemptLimiter.recordFailure(challenge.challengeId());
            throw new AuthException(HttpStatus.UNAUTHORIZED, "RECOVERY_CODE_INVALID",
                    "That recovery code is invalid or has already been used.");
        }
        SessionService.IssuedSession session = sessionService.create(user, request);
        attemptLimiter.clear(challenge.challengeId());
        pendingLoginService.consume(challenge);
        return new LoginCompletion(authenticated(user), session.token());
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getRole(), user.getCreatedAt(), user.isEmailVerified(), user.isTotpEnabled());
    }

    @Transactional
    public void deleteAccount(UUID userId, String password) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID",
                        "Your session is no longer valid. Sign in again."));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD",
                    "Current password is incorrect.");
        }
        userRepository.delete(user);
        userRepository.flush();
    }

    private User verifiedUserForUpdate(java.util.UUID userId) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(this::challengeInvalid);
        if (!user.isEmailVerified()) {
            throw challengeInvalid();
        }
        return user;
    }

    private String decryptPendingSecret(User user) {
        if (user.getTotpSecretEncrypted() == null) {
            throw challengeInvalid();
        }
        return encryptionService.decrypt(user.getTotpSecretEncrypted());
    }

    private LoginResponse authenticated(User user) {
        return new LoginResponse(AuthStatus.AUTHENTICATED, "Signed in successfully.", toResponse(user));
    }

    private AuthException invalidTotp() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "TOTP_INVALID",
                "The authenticator code is invalid. Try the current 6-digit code.");
    }

    private AuthException challengeInvalid() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "LOGIN_CHALLENGE_INVALID",
                "Your sign-in challenge expired. Enter your email and password again.");
    }
}

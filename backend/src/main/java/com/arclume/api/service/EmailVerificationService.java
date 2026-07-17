package com.arclume.api.service;

import com.arclume.api.domain.EmailVerificationToken;
import com.arclume.api.domain.User;
import com.arclume.api.repository.EmailVerificationTokenRepository;
import com.arclume.api.repository.UserRepository;
import com.arclume.api.security.AuthException;
import com.arclume.api.security.TokenHashService;
import com.arclume.api.service.email.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final TokenHashService tokenHashService;
    private final EmailService emailService;

    @Value("${app.security.auth.verification-hours:24}")
    private long verificationHours;

    @Value("${app.security.auth.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                                    UserRepository userRepository,
                                    TokenHashService tokenHashService,
                                    EmailService emailService) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.tokenHashService = tokenHashService;
        this.emailService = emailService;
    }

    @Transactional
    public String issueAndSend(User user) {
        String rawToken = issueToken(user);
        emailService.sendVerificationEmail(user,
                frontendBaseUrl + "/verify-email?token=" + rawToken);
        return rawToken;
    }

    @Transactional
    public String issueToken(User user) {
        String rawToken = tokenHashService.randomUrlToken(32);
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(tokenHashService.hash(rawToken));
        token.setExpiresAt(Instant.now().plus(Duration.ofHours(verificationHours)));
        tokenRepository.save(token);
        return rawToken;
    }

    @Transactional
    public void resend(String email) {
        if (email == null) {
            return;
        }
        userRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    Instant cooldownCutoff = Instant.now().minusSeconds(resendCooldownSeconds);
                    boolean coolingDown = tokenRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                            .map(token -> token.getCreatedAt().isAfter(cooldownCutoff))
                            .orElse(false);
                    if (!coolingDown) {
                        issueAndSend(user);
                    }
                });
    }

    @Transactional
    public User verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        EmailVerificationToken token = tokenRepository.findByTokenHash(tokenHashService.hash(rawToken))
                .orElseThrow(this::invalidToken);
        Instant now = Instant.now();
        if (token.getConsumedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }
        token.setConsumedAt(now);
        User user = token.getUser();
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(now);
        return user;
    }

    private AuthException invalidToken() {
        return new AuthException(HttpStatus.BAD_REQUEST, "VERIFICATION_TOKEN_INVALID",
                "This verification link is invalid or has expired.");
    }
}

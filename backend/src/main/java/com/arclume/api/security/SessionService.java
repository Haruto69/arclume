package com.arclume.api.security;

import com.arclume.api.domain.User;
import com.arclume.api.domain.UserSession;
import com.arclume.api.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    public record IssuedSession(String token, Instant expiresAt) {}

    private final UserSessionRepository userSessionRepository;
    private final TokenHashService tokenHashService;
    private final AuthCookieService authCookieService;

    public SessionService(UserSessionRepository userSessionRepository,
                          TokenHashService tokenHashService,
                          AuthCookieService authCookieService) {
        this.userSessionRepository = userSessionRepository;
        this.tokenHashService = tokenHashService;
        this.authCookieService = authCookieService;
    }

    @Transactional
    public IssuedSession create(User user, HttpServletRequest request) {
        return create(user, request.getHeader("User-Agent"), request.getRemoteAddr());
    }

    @Transactional
    public IssuedSession create(User user, String userAgent, String ipAddress) {
        String rawToken = tokenHashService.randomUrlToken(32);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(authCookieService.sessionDuration());

        UserSession session = new UserSession();
        session.setUser(user);
        session.setSessionTokenHash(tokenHashService.hash(rawToken));
        session.setExpiresAt(expiresAt);
        session.setLastUsedAt(now);
        session.setUserAgent(truncate(userAgent, 1024));
        session.setIpAddress(truncate(ipAddress, 255));
        userSessionRepository.save(session);

        return new IssuedSession(rawToken, expiresAt);
    }

    @Transactional
    public Optional<User> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<UserSession> result = userSessionRepository.findBySessionTokenHash(tokenHashService.hash(rawToken));
        if (result.isEmpty()) {
            return Optional.empty();
        }
        UserSession session = result.get();
        Instant now = Instant.now();
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        session.setLastUsedAt(now);
        User user = session.getUser();
        user.getRole();
        return Optional.of(user);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        userSessionRepository.findBySessionTokenHash(tokenHashService.hash(rawToken))
                .filter(session -> session.getRevokedAt() == null)
                .ifPresent(session -> session.setRevokedAt(Instant.now()));
    }

    @Transactional
    public int revokeAll(UUID userId) {
        return userSessionRepository.revokeAllForUser(userId, Instant.now());
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

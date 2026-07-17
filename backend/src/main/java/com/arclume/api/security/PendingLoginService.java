package com.arclume.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PendingLoginService {

    public enum Stage {
        TOTP_SETUP_REQUIRED,
        TOTP_REQUIRED
    }

    public record Challenge(UUID userId, String challengeId, Stage stage) {}

    @Value("${app.security.auth.challenge-secret:}")
    private String challengeSecret;

    @Value("${app.security.auth.challenge-minutes:10}")
    private long challengeMinutes;

    private SecretKey key;
    private final ConcurrentHashMap<String, Instant> consumedChallenges = new ConcurrentHashMap<>();

    @PostConstruct
    void initialize() {
        if (challengeSecret == null || challengeSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("AUTH_CHALLENGE_SECRET must be at least 32 bytes");
        }
        key = Keys.hmacShaKeyFor(challengeSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String create(UUID userId, Stage stage) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim("stage", stage.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(challengeMinutes))))
                .signWith(key)
                .compact();
    }

    public Challenge validate(String token, Stage requiredStage) {
        if (token == null || token.isBlank()) {
            throw invalidChallenge();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            Stage stage = Stage.valueOf(claims.get("stage", String.class));
            if (stage != requiredStage) {
                throw invalidChallenge();
            }
            Challenge challenge = new Challenge(UUID.fromString(claims.getSubject()), claims.getId(), stage);
            ensureActive(challenge);
            return challenge;
        } catch (AuthException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            throw invalidChallenge();
        }
    }

    public void ensureActive(Challenge challenge) {
        Instant consumedUntil = consumedChallenges.get(challenge.challengeId());
        if (consumedUntil != null && consumedUntil.isAfter(Instant.now())) {
            throw invalidChallenge();
        }
    }

    public void consume(Challenge challenge) {
        Instant now = Instant.now();
        consumedChallenges.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        consumedChallenges.put(challenge.challengeId(), now.plus(Duration.ofMinutes(challengeMinutes)));
    }

    private AuthException invalidChallenge() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "LOGIN_CHALLENGE_INVALID",
                "Your sign-in challenge expired. Enter your email and password again.");
    }
}

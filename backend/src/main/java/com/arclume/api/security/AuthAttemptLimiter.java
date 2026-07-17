package com.arclume.api.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthAttemptLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public void checkAllowed(String challengeId) {
        Deque<Instant> attempts = failures.computeIfAbsent(challengeId, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            removeExpired(attempts);
            if (attempts.size() >= MAX_ATTEMPTS) {
                throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                        "Too many authentication attempts. Try again in a few minutes.");
            }
        }
    }

    public void recordFailure(String challengeId) {
        Deque<Instant> attempts = failures.computeIfAbsent(challengeId, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            removeExpired(attempts);
            attempts.addLast(Instant.now());
        }
    }

    public void clear(String challengeId) {
        failures.remove(challengeId);
    }

    private void removeExpired(Deque<Instant> attempts) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.removeFirst();
        }
    }
}

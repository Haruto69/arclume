package com.arclume.api.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthCookieService {

    public static final String SESSION_COOKIE = "ARCLUME_SESSION";
    public static final String CHALLENGE_COOKIE = "ARCLUME_LOGIN_CHALLENGE";

    @Value("${app.security.auth.cookie.secure:true}")
    private boolean secure;

    @Value("${app.security.auth.cookie.samesite:Strict}")
    private String sameSite;

    @Value("${app.security.auth.cookie.path:/}")
    private String path;

    @Value("${app.security.auth.session-days:30}")
    private long sessionDays;

    @Value("${app.security.auth.challenge-minutes:10}")
    private long challengeMinutes;

    public void setSessionCookie(HttpServletResponse response, String token) {
        addCookie(response, SESSION_COOKIE, token, Duration.ofDays(sessionDays));
    }

    public void clearSessionCookie(HttpServletResponse response) {
        addCookie(response, SESSION_COOKIE, "", Duration.ZERO);
    }

    public void setChallengeCookie(HttpServletResponse response, String token) {
        addCookie(response, CHALLENGE_COOKIE, token, Duration.ofMinutes(challengeMinutes));
    }

    public void clearChallengeCookie(HttpServletResponse response) {
        addCookie(response, CHALLENGE_COOKIE, "", Duration.ZERO);
    }

    public String sessionToken(HttpServletRequest request) {
        return cookieValue(request, SESSION_COOKIE);
    }

    public String challengeToken(HttpServletRequest request) {
        return cookieValue(request, CHALLENGE_COOKIE);
    }

    public Duration sessionDuration() {
        return Duration.ofDays(sessionDays);
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void addCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

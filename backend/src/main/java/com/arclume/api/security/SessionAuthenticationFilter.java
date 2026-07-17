package com.arclume.api.security;

import com.arclume.api.domain.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final AuthCookieService authCookieService;

    public SessionAuthenticationFilter(SessionService sessionService, AuthCookieService authCookieService) {
        this.sessionService = sessionService;
        this.authCookieService = authCookieService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = authCookieService.sessionToken(request);
            sessionService.authenticate(token).ifPresent(user -> authenticate(request, user));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, User user) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}

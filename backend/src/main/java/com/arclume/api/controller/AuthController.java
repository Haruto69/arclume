package com.arclume.api.controller;

import com.arclume.api.domain.User;
import com.arclume.api.dto.AuthMessageResponse;
import com.arclume.api.dto.DeleteAccountRequest;
import com.arclume.api.dto.EmailRequest;
import com.arclume.api.dto.LoginRequest;
import com.arclume.api.dto.LoginResponse;
import com.arclume.api.dto.RecoveryCodeLoginRequest;
import com.arclume.api.dto.RegisterRequest;
import com.arclume.api.dto.RegistrationResponse;
import com.arclume.api.dto.TotpCodeRequest;
import com.arclume.api.dto.TotpConfirmResponse;
import com.arclume.api.dto.TotpSetupResponse;
import com.arclume.api.dto.UserResponse;
import com.arclume.api.security.AuthCookieService;
import com.arclume.api.security.SessionService;
import com.arclume.api.service.AuthenticationService;
import com.arclume.api.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final EmailVerificationService emailVerificationService;
    private final SessionService sessionService;
    private final AuthCookieService authCookieService;

    public AuthController(AuthenticationService authenticationService,
                          EmailVerificationService emailVerificationService,
                          SessionService sessionService,
                          AuthCookieService authCookieService) {
        this.authenticationService = authenticationService;
        this.emailVerificationService = emailVerificationService;
        this.sessionService = sessionService;
        this.authCookieService = authCookieService;
    }

    @GetMapping("/csrf")
    public void csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute("_csrf");
        }
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        if (csrfToken == null) {
            csrfToken = repository.generateToken(request);
        }
        csrfToken.getToken();
        repository.saveToken(csrfToken, request, response);
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authenticationService.register(request));
    }

    @GetMapping("/verify-email")
    public AuthMessageResponse verifyEmail(@RequestParam(required = false) String token) {
        emailVerificationService.verify(token);
        return new AuthMessageResponse("EMAIL_VERIFIED", "Your email is verified. You can now sign in.");
    }

    @PostMapping("/resend-verification")
    public AuthMessageResponse resendVerification(@Valid @RequestBody EmailRequest request) {
        emailVerificationService.resend(request.email());
        return new AuthMessageResponse("VERIFICATION_SENT",
                "If that account needs verification, a new link has been sent.");
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthenticationService.PasswordStep step = authenticationService.passwordLogin(request);
        if (step.challengeToken() == null) {
            authCookieService.clearChallengeCookie(response);
        } else {
            authCookieService.setChallengeCookie(response, step.challengeToken());
        }
        return step.response();
    }

    @PostMapping("/totp/setup/start")
    public TotpSetupResponse startTotpSetup(HttpServletRequest request) {
        return authenticationService.startTotpSetup(authCookieService.challengeToken(request));
    }

    @PostMapping("/totp/setup/confirm")
    public TotpConfirmResponse confirmTotpSetup(@Valid @RequestBody TotpCodeRequest request,
                                                HttpServletRequest servletRequest,
                                                HttpServletResponse response) {
        AuthenticationService.SetupCompletion completion = authenticationService.confirmTotpSetup(
                authCookieService.challengeToken(servletRequest), request.code(), servletRequest);
        authCookieService.setSessionCookie(response, completion.sessionToken());
        authCookieService.clearChallengeCookie(response);
        return completion.response();
    }

    @PostMapping("/login/totp")
    public LoginResponse loginTotp(@Valid @RequestBody TotpCodeRequest request,
                                   HttpServletRequest servletRequest,
                                   HttpServletResponse response) {
        AuthenticationService.LoginCompletion completion = authenticationService.completeTotpLogin(
                authCookieService.challengeToken(servletRequest), request.code(), servletRequest);
        authCookieService.setSessionCookie(response, completion.sessionToken());
        authCookieService.clearChallengeCookie(response);
        return completion.response();
    }

    @PostMapping("/recovery-code/login")
    public LoginResponse loginWithRecoveryCode(@Valid @RequestBody RecoveryCodeLoginRequest request,
                                               HttpServletRequest servletRequest,
                                               HttpServletResponse response) {
        AuthenticationService.LoginCompletion completion = authenticationService.completeRecoveryLogin(
                authCookieService.challengeToken(servletRequest), request.code(), servletRequest);
        authCookieService.setSessionCookie(response, completion.sessionToken());
        authCookieService.clearChallengeCookie(response);
        return completion.response();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessionService.revoke(authCookieService.sessionToken(request));
        authCookieService.clearSessionCookie(response);
        authCookieService.clearChallengeCookie(response);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(Authentication authentication, HttpServletResponse response) {
        User user = requireUser(authentication);
        sessionService.revokeAll(user.getId());
        authCookieService.clearSessionCookie(response);
        authCookieService.clearChallengeCookie(response);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/account")
    public AuthMessageResponse deleteAccount(@Valid @RequestBody DeleteAccountRequest request,
                                             Authentication authentication,
                                             HttpServletResponse response) {
        User user = requireUser(authentication);
        authenticationService.deleteAccount(user.getId(), request.password());
        authCookieService.clearSessionCookie(response);
        authCookieService.clearChallengeCookie(response);
        return new AuthMessageResponse("ACCOUNT_DELETED", "Your account has been permanently deleted.");
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return authenticationService.toResponse(requireUser(authentication));
    }

    private User requireUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        throw new org.springframework.security.authentication.InsufficientAuthenticationException(
                "User is not authenticated");
    }
}

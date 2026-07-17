package com.arclume.api.dto;

public enum AuthStatus {
    VERIFICATION_REQUIRED,
    EMAIL_NOT_VERIFIED,
    TOTP_SETUP_REQUIRED,
    TOTP_REQUIRED,
    AUTHENTICATED
}

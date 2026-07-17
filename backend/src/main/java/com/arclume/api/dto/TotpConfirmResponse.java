package com.arclume.api.dto;

import java.util.List;

public record TotpConfirmResponse(
        AuthStatus status,
        String message,
        UserResponse user,
        List<String> recoveryCodes
) {}

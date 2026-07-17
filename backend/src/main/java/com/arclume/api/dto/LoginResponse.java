package com.arclume.api.dto;

public record LoginResponse(AuthStatus status, String message, UserResponse user) {}

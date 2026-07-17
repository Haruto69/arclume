package com.arclume.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecoveryCodeLoginRequest(@NotBlank @Size(max = 64) String code) {}

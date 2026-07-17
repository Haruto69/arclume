package com.arclume.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TotpCodeRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Code must contain exactly 6 digits") String code
) {}

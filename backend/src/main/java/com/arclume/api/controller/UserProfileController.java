package com.arclume.api.controller;

import com.arclume.api.domain.User;
import com.arclume.api.dto.UpdateUserProfileRequest;
import com.arclume.api.dto.UserProfileResponse;
import com.arclume.api.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping
    public UserProfileResponse getProfile(Authentication authentication) {
        return userProfileService.getProfile(requireUser(authentication));
    }

    @PutMapping
    public UserProfileResponse updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        User user = requireUser(authentication);
        return userProfileService.updateProfile(user.getId(), request);
    }

    private User requireUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        throw new org.springframework.security.authentication.InsufficientAuthenticationException(
                "User is not authenticated");
    }
}

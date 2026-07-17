package com.arclume.api.service.email;

import com.arclume.api.domain.User;

public interface EmailService {
    void sendVerificationEmail(User user, String verificationUrl);
}

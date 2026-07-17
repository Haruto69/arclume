package com.arclume.api.service.email;

import com.arclume.api.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.email.delivery", havingValue = "disabled")
public class DisabledEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(DisabledEmailService.class);

    @Override
    public void sendVerificationEmail(User user, String verificationUrl) {
        log.warn("Verification email delivery is disabled for account {}", user.getEmail());
    }
}

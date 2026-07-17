package com.arclume.api.security;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;

@Service
public class TotpService {

    private static final int SECRET_BYTES = 20;
    private static final long TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int CLOCK_SKEW_STEPS = 1;

    private final TokenHashService tokenHashService;
    private final Base32 base32 = new Base32();

    public TotpService(TokenHashService tokenHashService) {
        this.tokenHashService = tokenHashService;
    }

    public String generateSecret() {
        return base32.encodeToString(tokenHashService.randomBytes(SECRET_BYTES))
                .replace("=", "");
    }

    public String buildOtpAuthUri(String email, String secret) {
        String label = encode("Arclume:" + email);
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + encode("Arclume")
                + "&algorithm=SHA1&digits=" + DIGITS
                + "&period=" + TIME_STEP_SECONDS;
    }

    public boolean verify(String secret, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            return false;
        }
        long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        for (int offset = -CLOCK_SKEW_STEPS; offset <= CLOCK_SKEW_STEPS; offset++) {
            String expected = generateCode(secret, currentStep + offset);
            if (MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
        }
        return false;
    }

    public String currentCode(String secret) {
        return generateCode(secret, Instant.now().getEpochSecond() / TIME_STEP_SECONDS);
    }

    private String generateCode(String secret, long step) {
        try {
            byte[] decodedSecret = base32.decode(secret);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodedSecret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int value = binary % 1_000_000;
            return String.format(Locale.ROOT, "%06d", value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate TOTP code", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

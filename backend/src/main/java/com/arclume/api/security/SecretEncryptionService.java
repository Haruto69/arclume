package com.arclume.api.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class SecretEncryptionService {

    private static final String VERSION = "v1.";
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final TokenHashService tokenHashService;

    @Value("${app.security.auth.encryption-key:}")
    private String encryptionKey;

    private SecretKeySpec key;

    public SecretEncryptionService(TokenHashService tokenHashService) {
        this.tokenHashService = tokenHashService;
    }

    @PostConstruct
    void initialize() {
        if (encryptionKey == null || encryptionKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("AUTH_ENCRYPTION_KEY must be at least 32 bytes");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
            key = new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize secret encryption", e);
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = tokenHashService.randomBytes(IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            return VERSION + Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt authentication secret", e);
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || !encrypted.startsWith(VERSION)) {
            throw new IllegalStateException("Unsupported authentication secret format");
        }
        try {
            byte[] packed = Base64.getUrlDecoder().decode(encrypted.substring(VERSION.length()));
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to decrypt authentication secret", e);
        }
    }
}

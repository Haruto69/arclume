package com.arclume.api.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileSystemUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class BaseIntegrationTest {

    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");
    private static final Path RESUME_STORAGE_DIRECTORY = createResumeStorageDirectory();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(BaseIntegrationTest::deleteResumeStorageDirectory));
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.auth.challenge-secret",
                () -> "secure_challenge_secret_at_least_32_bytes_long_for_test");
        registry.add("app.security.auth.encryption-key",
                () -> "secure_encryption_key_at_least_32_bytes_long_for_test");
        registry.add("app.security.auth.cookie.secure", () -> false);
        registry.add("app.email.delivery", () -> "disabled");
        registry.add("app.storage.resumes-dir", () -> RESUME_STORAGE_DIRECTORY.toString());
    }

    private static Path createResumeStorageDirectory() {
        try {
            return Files.createTempDirectory("arclume-test-resumes-");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void deleteResumeStorageDirectory() {
        try {
            FileSystemUtils.deleteRecursively(RESUME_STORAGE_DIRECTORY);
        } catch (IOException e) {
            RESUME_STORAGE_DIRECTORY.toFile().deleteOnExit();
            System.err.println("Failed to delete temporary resume storage: " + e.getMessage());
        }
    }
}
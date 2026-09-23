package com.amigoscode.interview.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import com.amigoscode.interview.SecretStorageApplication;

/**
 * The app must refuse to start without a usable secret, rather than start and fail later.
 */
class StartupValidationTest {

    @Test
    void refusesToStartWithoutAHash() {
        assertThatThrownBy(() -> start("--app.admin.password-hash="))
                .rootCause()
                .hasMessageContaining("passwordHash")
                .hasMessageContaining("ADMIN_PASSWORD_HASH");
    }

    @Test
    void refusesToStartWithAPlaintextPassword() {
        assertThatThrownBy(() -> start("--app.admin.password-hash=SuperSecretPassword123!"))
                .rootCause()
                .hasMessageContaining("must be a bcrypt hash");
    }

    // Passed as a command-line argument, which outranks application.yml and any
    // ADMIN_PASSWORD_HASH on the machine. Default properties would be overridden by both.
    private static void start(String argument) {
        new SpringApplicationBuilder(SecretStorageApplication.class)
                .run(argument, "--server.port=0")
                .close();
    }

}

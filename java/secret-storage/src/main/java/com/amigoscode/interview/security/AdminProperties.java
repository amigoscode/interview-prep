package com.amigoscode.interview.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The admin account. The password is never configured, only its bcrypt hash, which comes from the
 * environment (ADMIN_PASSWORD_HASH). Validation runs at startup, so a missing or plaintext value
 * stops the app with a message naming the property instead of failing on the first request.
 */
@Validated
@ConfigurationProperties("app.admin")
public record AdminProperties(

        @NotBlank
        String username,

        @NotBlank(message = "is not set: export ADMIN_PASSWORD_HASH, see .env.example")
        @Pattern(regexp = "^$|^\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$",
                message = "must be a bcrypt hash, not a password: see .env.example")
        String passwordHash
) {
}

package com.webcollector.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuthConfigTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(AuthConfig.class);

    @Test
    void shouldBindRegistrationEnabled() {
        contextRunner
                .withPropertyValues("app.auth.registration-enabled=true")
                .run(context -> {
                    AuthProperties properties =
                            context.getBean(AuthProperties.class);
                    assertTrue(properties.isRegistrationEnabled());
                });
    }

    @Test
    void shouldDefaultRegistrationToFalse() {
        contextRunner.run(context -> {
            AuthProperties properties =
                    context.getBean(AuthProperties.class);

            assertFalse(properties.isRegistrationEnabled());
        });
    }

    @Test
    void shouldProvideBcryptPasswordEncoder() {
        contextRunner.run(context -> {
            PasswordEncoder encoder =
                    context.getBean(PasswordEncoder.class);

            String rawPassword = "correct-password";
            String wrongPassword = "wrong-password";

            String hash = encoder.encode(rawPassword);

            assertNotEquals(rawPassword, hash);
            assertTrue(encoder.matches(rawPassword, hash));
            assertFalse(encoder.matches(wrongPassword, hash));
        });
    }
}

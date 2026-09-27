package com.webcollector.auth.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthInputNormalizerTest {

    @Test
    void shouldStripSurroundingWhitespaceFromUsername() {
        assertThat(AuthInputNormalizer.normalizeUsername("  student  "))
                .isEqualTo("student");
    }

    @Test
    void shouldStripFullWidthSpaceFromUsername() {
        assertThat(AuthInputNormalizer.normalizeUsername("\u3000student\u3000"))
                .isEqualTo("student");
    }

    @Test
    void shouldKeepNullUsernameAsNull() {
        assertThat(AuthInputNormalizer.normalizeUsername(null)).isNull();
    }

    @Test
    void shouldLowercaseEmailAndStripWhitespace() {
        assertThat(AuthInputNormalizer.normalizeEmail("  Student@Example.COM  "))
                .isEqualTo("student@example.com");
    }

    @Test
    void shouldConvertBlankEmailToNull() {
        assertThat(AuthInputNormalizer.normalizeEmail("")).isNull();
        assertThat(AuthInputNormalizer.normalizeEmail("   ")).isNull();
        assertThat(AuthInputNormalizer.normalizeEmail("\u3000")).isNull();
    }

    @Test
    void shouldKeepNullEmailAsNull() {
        assertThat(AuthInputNormalizer.normalizeEmail(null)).isNull();
    }
}

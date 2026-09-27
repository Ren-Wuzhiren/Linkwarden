package com.webcollector.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestValidationTest {
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation
                .buildDefaultValidatorFactory()
                .getValidator();
    }

    @Test
    void shouldAcceptValidRequestAndNormalizeInput() {
        RegisterRequest request = new RegisterRequest(
                "  student  ",
                "  Student@Example.COM  ",
                "correct-horse"
        );

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.username()).isEqualTo("student");
        assertThat(request.email()).isEqualTo("student@example.com");
    }

    @Test
    void shouldRejectUsernameTooShortAfterStripping() {
        RegisterRequest request = new RegisterRequest(
                "  ab  ",
                null,
                "correct-horse"
        );

        assertThat(messagesFor(request, "username"))
                .containsExactly("用户名长度必须在 3 到 32 个字符之间");
    }

    @Test
    void shouldRejectBlankUsername() {
        RegisterRequest request = new RegisterRequest(
                "\u3000  ",
                null,
                "correct-horse"
        );

        assertThat(propertyPaths(request)).contains("username");
    }

    @Test
    void shouldRejectMalformedEmail() {
        RegisterRequest request = new RegisterRequest(
                "student",
                "not-an-email",
                "correct-horse"
        );

        assertThat(messagesFor(request, "email"))
                .containsExactly("邮箱格式不正确");
    }

    @Test
    void shouldAcceptMissingEmail() {
        RegisterRequest request = new RegisterRequest(
                "student",
                "   ",
                "correct-horse"
        );

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.email()).isNull();
    }

    @Test
    void shouldRejectPasswordShorterThanEightCharacters() {
        RegisterRequest request = new RegisterRequest(
                "student",
                null,
                "short"
        );

        assertThat(messagesFor(request, "password"))
                .containsExactly("密码长度必须在 8 到 72 个字符之间");
    }

    @Test
    void shouldNotModifyPassword() {
        RegisterRequest request = new RegisterRequest(
                "student",
                null,
                "  spaces  "
        );

        assertThat(request.password()).isEqualTo("  spaces  ");
    }

    private static Set<String> propertyPaths(RegisterRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static Set<String> messagesFor(
            RegisterRequest request,
            String property
    ) {
        return validator.validate(request).stream()
                .filter(violation -> violation
                        .getPropertyPath()
                        .toString()
                        .equals(property))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}

package com.webcollector.auth.dto;

import com.webcollector.auth.support.AuthInputNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest (
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度必须在 3 到 32 个字符之间")
        String username,

        @Email(message = "邮箱格式不正确")
        @Size(max = 254, message = "邮箱长度不能超过 254 个字符")
        String email,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度必须在 8 到 72 个字符之间")
        String password
) {
    public RegisterRequest {
        username = AuthInputNormalizer.normalizeUsername(username);
        email = AuthInputNormalizer.normalizeEmail(email);
    }
}
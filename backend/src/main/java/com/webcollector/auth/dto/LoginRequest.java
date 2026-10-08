package com.webcollector.auth.dto;

import com.webcollector.auth.support.AuthInputNormalizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest (
        @NotBlank(message = "用户名不能为空")
        @Size(max = 32, message = "用户名长度不能超过 32 个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(max = 72, message = "密码长度不能超过 72 个字符")
        String password
) {
    public LoginRequest {
        // [业务] 登录和注册必须使用相同的用户名规范化规则。
        username = AuthInputNormalizer.normalizeUsername(username);
    }
}
package com.webcollector.auth.support;

import java.util.Locale;

public final class AuthInputNormalizer {
    // 静态方法不该被 new
    private AuthInputNormalizer() {
    }

    public static String normalizeUsername(String rawUsername) {
        if (rawUsername == null) {
            return null;
        }

        return rawUsername.strip();
    }

    public static String normalizeEmail(String rawEmail) {
        if (rawEmail == null) {
            return null;
        }

        String stripped = rawEmail.strip();

        if (stripped.isEmpty()) {
            return null;
        }

        // 规范化邮箱时去除语言的字母转写影响
        // strip 可以 去除全角空格 \u3000
        return stripped.toLowerCase(Locale.ROOT);
    }
}

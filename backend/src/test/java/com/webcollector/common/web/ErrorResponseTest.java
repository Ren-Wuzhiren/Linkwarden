package com.webcollector.common.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorResponseTest {

    @AfterEach
    void cleanup() {
        RequestIdContext.clear();
    }

    @Test
    void shouldCreateDefaultErrorResponse() {
        RequestIdContext.set("req-error-001");

        ErrorResponse response = ErrorResponse.from(ErrorCode.NOT_FOUND);

        assertEquals("NOT_FOUND", response.getCode());
        assertEquals("资源不存在", response.getMessage());
        assertTrue(response.getDetails().isEmpty());
        assertEquals("req-error-001", response.getRequestId());
    }

    @Test
    void shouldCreateCustomErrorResponse() {
        RequestIdContext.set("req-error-002");
        Map<String, String> details = new LinkedHashMap<>();
        details.put("username", "用户名已存在");

        ErrorResponse response = ErrorResponse.from(
                ErrorCode.CONFLICT,
                "用户名冲突",
                details
        );

        assertEquals("CONFLICT", response.getCode());
        assertEquals("用户名冲突", response.getMessage());
        assertEquals("用户名已存在", response.getDetails().get("username"));
        assertEquals("req-error-002", response.getRequestId());
    }

    @Test
    void shouldNormalizeNullDetailsToEmptyMap() {
        RequestIdContext.set("req-error-003");

        ErrorResponse response = ErrorResponse.from(
                ErrorCode.INTERNAL_ERROR,
                "系统内部错误",
                null
        );

        assertTrue(response.getDetails().isEmpty());
    }

    @Test
    void shouldKeepDetailsSnapshot() {
        RequestIdContext.set("req-error-004");
        Map<String, String> details = new LinkedHashMap<>();
        details.put("field", "original");

        ErrorResponse response = ErrorResponse.from(
                ErrorCode.VALIDATION_ERROR,
                "请求参数不合法",
                details
        );

        details.put("field", "changed");

        assertEquals("original", response.getDetails().get("field"));
    }

    @Test
    void shouldKeepRequestIdSnapshot() {
        RequestIdContext.set("req-error-005");

        ErrorResponse response = ErrorResponse.from(ErrorCode.CONFLICT);

        RequestIdContext.set("req-error-006");

        assertEquals("req-error-005", response.getRequestId());
    }
}

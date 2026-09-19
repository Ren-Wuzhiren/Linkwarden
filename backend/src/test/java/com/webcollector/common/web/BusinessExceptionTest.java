package com.webcollector.common.web;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessExceptionTest {

    @Test
    void shouldUseErrorCodeDefaults() {
        BusinessException exception = new BusinessException(ErrorCode.NOT_FOUND);

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        assertEquals("资源不存在", exception.getMessage());
        assertTrue(exception.getDetails().isEmpty());
    }

    @Test
    void shouldCreateExceptionWithCustomMessageAndDetails() {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("username", "用户名已存在");

        BusinessException exception = new BusinessException(
                ErrorCode.CONFLICT,
                "用户名冲突",
                details
        );

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals("用户名冲突", exception.getMessage());
        assertEquals("用户名已存在", exception.getDetails().get("username"));
    }

    @Test
    void shouldKeepDetailsSnapshot() {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("field", "original");

        BusinessException exception = new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "参数错误",
                details
        );

        details.put("field", "changed");

        assertEquals("original", exception.getDetails().get("field"));
    }
}

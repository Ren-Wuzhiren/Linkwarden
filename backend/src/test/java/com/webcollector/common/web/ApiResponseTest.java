package com.webcollector.common.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiResponseTest {

    @AfterEach
    void cleanup() {
        RequestIdContext.clear();
    }

    @Test
    void shouldCreateDefaultSuccessResponse() {
        RequestIdContext.set("req-api-001");

        ApiResponse<String> response = ApiResponse.success("hello");

        assertEquals("hello", response.getData());
        assertEquals("success", response.getMessage());
        assertEquals("req-api-001", response.getRequestId());
    }

    @Test
    void shouldUseCustomMessage() {
        RequestIdContext.set("req-api-002");

        ApiResponse<String> response = ApiResponse.success("test-001", "hello-test");

        assertEquals("test-001", response.getData());
        assertEquals("hello-test", response.getMessage());
        assertEquals("req-api-002", response.getRequestId());
    }

    @Test
    void shouldSnapshotRequestId() {
        RequestIdContext.set("req-api-003");

        ApiResponse<String> response = ApiResponse.success("test-002", "successfully");

        RequestIdContext.set("req-api-004");

        assertEquals("test-002", response.getData());
        assertEquals("successfully", response.getMessage());
        assertEquals("req-api-003", response.getRequestId());
    }
}

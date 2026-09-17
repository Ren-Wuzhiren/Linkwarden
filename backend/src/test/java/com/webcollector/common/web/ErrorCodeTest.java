package com.webcollector.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorCodeTest {

    @Test
    void shouldMapErrorCodesToHttpStatuses() {
        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.getStatus()),
                () -> assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST.getStatus()),
                () -> assertEquals(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getStatus()),
                () -> assertEquals(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.getStatus()),
                () -> assertEquals(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND.getStatus()),
                () -> assertEquals(HttpStatus.CONFLICT, ErrorCode.CONFLICT.getStatus()),
                () -> assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.getStatus()),
                () -> assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE.getStatus())
        );
    }

    @Test
    void shouldProvideDefaultMessageForEveryErrorCode() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertNotNull(errorCode.getDefaultMessage());
            assertFalse(errorCode.getDefaultMessage().isBlank());
        }
    }
}

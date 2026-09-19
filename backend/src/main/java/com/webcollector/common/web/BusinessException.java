package com.webcollector.common.web;

import java.util.Map;

public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, String> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), Map.of());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public BusinessException(
            ErrorCode errorCode,
            String message,
            Map<String, String> details
    ) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

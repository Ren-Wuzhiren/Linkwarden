package com.webcollector.common.web;

import java.util.Map;

public final class ErrorResponse {
    private final String code;
    private final String message;
    private final Map<String, String> details;
    private final String requestId;

    private ErrorResponse(String code, String message, Map<String, String> details, String requestId) {
        this.code = code;
        this.message = message;
        this.details = details == null ? Map.of() : Map.copyOf(details);
        this.requestId = requestId;
    }

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.name(),
                errorCode.getDefaultMessage(),
                Map.of(),
                RequestIdContext.get()
        );
    }

    public static ErrorResponse from(
            ErrorCode errorCode,
            String message,
            Map<String, String> details
    ) {
        return new ErrorResponse(
                errorCode.name(),
                message,
                details,
                RequestIdContext.get()
        );
    }

    public String getCode() {
        return code;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public String getMessage() {
        return message;
    }

    public String getRequestId() {
        return requestId;
    }
}

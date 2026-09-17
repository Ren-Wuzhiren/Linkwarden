package com.webcollector.common.web;

public final class ApiResponse<T> {
    private final T data;
    private final String message;
    private final String requestId;

    private ApiResponse(T data, String message, String requestId) {
        this.data = data;
        this.message = message;
        this.requestId = requestId;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, "success", RequestIdContext.get());
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(data, message, RequestIdContext.get());
    }

    public T getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

    public String getRequestId() {
        return requestId;
    }
}

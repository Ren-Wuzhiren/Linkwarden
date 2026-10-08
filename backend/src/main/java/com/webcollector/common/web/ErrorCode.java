package com.webcollector.common.web;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    /*
        VALIDATION_ERROR            400
        INVALID_REQUEST             400
        UNAUTHORIZED                401
        AUTH_REQUIRED               401
        AUTH_INVALID_CREDENTIALS    401
        FORBIDDEN                   403
        NOT_FOUND                   404
        CONFLICT                    409
        INTERNAL_ERROR              500
        SERVICE_UNAVAILABLE         503
    */

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "请求参数不合法"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "请求格式不正确"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "未认证"),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "请先登录"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "无权访问"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    CONFLICT(HttpStatus.CONFLICT, "资源冲突"),
    REGISTRATION_DISABLED(HttpStatus.FORBIDDEN, "当前环境未开放注册"),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "用户名已存在"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "邮箱已被使用"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误"),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "服务暂时不可用");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

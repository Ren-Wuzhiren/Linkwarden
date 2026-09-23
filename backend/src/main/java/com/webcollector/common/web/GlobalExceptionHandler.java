package com.webcollector.common.web;

import cn.dev33.satoken.exception.NotLoginException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        return build(
                errorCode,
                exception.getMessage(),
                exception.getDetails()
        );
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ErrorResponse> handleNotLoginException(
            NotLoginException exception
    ) {
        return build(
                ErrorCode.AUTH_REQUIRED,
                ErrorCode.AUTH_REQUIRED.getDefaultMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> details = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(error -> {
            String message = error.getDefaultMessage() == null
                    ? "参数校验失败"
                    : error.getDefaultMessage();

            details.putIfAbsent(error.getField(), message);
        });

        return build(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                details
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception
    ) {
        Map<String, String> details = new LinkedHashMap<>();

        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            String message = violation.getMessage() == null
                    ? "参数校验失败"
                    : violation.getMessage();

            details.putIfAbsent(
                    violation.getPropertyPath().toString(),
                    message
            );
        }

        return build(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                details
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception
    ) {
        return build(
                ErrorCode.INVALID_REQUEST,
                ErrorCode.INVALID_REQUEST.getDefaultMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknownException(
            Exception exception
    ) {
        log.error(
                "Unhandled exception. requestId={}",
                RequestIdContext.get(),
                exception
        );

        return build(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        return build(
                ErrorCode.INVALID_REQUEST,
                ErrorCode.INVALID_REQUEST.getDefaultMessage(),
                Map.of(exception.getName(), "参数类型不正确")
        );
    }

    private ResponseEntity<ErrorResponse> build(
            ErrorCode errorCode,
            String message,
            Map<String, String> details
    ) {
        ErrorResponse response = ErrorResponse.from(
                errorCode,
                message,
                details
        );

        return ResponseEntity.status(errorCode.getStatus()).body(response);
    }
}

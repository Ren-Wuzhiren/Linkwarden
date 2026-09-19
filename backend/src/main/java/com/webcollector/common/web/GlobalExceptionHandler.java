package com.webcollector.common.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        ErrorResponse response = ErrorResponse.from(
                errorCode,
                exception.getMessage(),
                exception.getDetails()
        );

        return ResponseEntity.status(errorCode.getStatus()).body(response);
    }
}

package com.ticketapp.controller.exception;

import com.ticketapp.application.exception.AppException;
import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.controller.common.ApiResponse;
import com.ticketapp.domain.order.IllegalOrderTransitionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        log.warn("app exception: {}", ex.getErrorCode());
        return build(ex.getErrorCode());
    }

    @ExceptionHandler(IllegalOrderTransitionException.class)
    ResponseEntity<ApiResponse<Void>> handleIllegalOrderTransition(IllegalOrderTransitionException ex) {
        log.warn("illegal order transition: {}", ex.getMessage());
        return build(ErrorCode.ILLEGAL_ORDER_TRANSITION);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : errorCode.getMessage();
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ApiResponse<>(errorCode.getCode(), message, null));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return build(ErrorCode.UNAUTHENTICATED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUncategorized(Exception ex) {
        log.error("unhandled exception", ex);
        return build(ErrorCode.UNCATEGORIZED);
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null));
    }
}

package com.ticketapp.controller.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(int code, String message, T result) {

    private static final int SUCCESS_CODE = 1000;

    public static <T> ApiResponse<T> ok(T result) {
        return new ApiResponse<>(SUCCESS_CODE, null, result);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(SUCCESS_CODE, null, null);
    }
}

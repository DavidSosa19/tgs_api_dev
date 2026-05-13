package com.example.tgs_dev.controller.response;

import java.time.LocalDateTime;
import java.util.List;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        List<ApiError> errors,
        LocalDateTime timestamp
) {
    public record ApiError(String field, String reason) {}

    // Factory methods
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data, List.of(), LocalDateTime.now());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, List.of(), LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String message, List<ApiError> errors) {
        return new ApiResponse<>(false, message, null, errors, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, List.of(), LocalDateTime.now());
    }
}

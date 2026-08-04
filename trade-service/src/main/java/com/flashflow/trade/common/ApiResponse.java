package com.flashflow.trade.common;

import java.time.Instant;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        Instant timestamp,
        String traceId
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("OK", "success", data, Instant.now(), TraceContext.currentTraceId());
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(code, message, null, Instant.now(), TraceContext.currentTraceId());
    }
}

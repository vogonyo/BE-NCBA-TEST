package com.backend.ncba.BE_Demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response envelope")
public record ApiResponse<T>(
        @Schema(description = "Whether the request succeeded", example = "true")
        boolean success,

        @Schema(description = "Human-readable message", example = "OK")
        String message,

        @Schema(description = "Response payload")
        T data,

        @Schema(description = "Response timestamp (ISO-8601)")
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data, Instant.now());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null, Instant.now());
    }
}

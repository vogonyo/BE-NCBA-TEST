package com.backend.ncba.BE_Demo.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Error response body")
public record ErrorResponse(
        @Schema(description = "HTTP status code", example = "404")
        int status,

        @Schema(description = "Short error category", example = "NOT_FOUND")
        String error,

        @Schema(description = "Human-readable message")
        String message,

        @Schema(description = "Request path", example = "/api/v1/countries/XX")
        String path,

        @Schema(description = "Field-level validation errors (optional)")
        List<FieldError> fieldErrors,

        @Schema(description = "Timestamp")
        Instant timestamp
) {
    public record FieldError(String field, String message) {}
}

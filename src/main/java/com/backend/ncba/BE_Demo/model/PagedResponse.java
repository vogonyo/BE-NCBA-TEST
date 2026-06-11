package com.backend.ncba.BE_Demo.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated response wrapper")
public record PagedResponse<T>(
        @Schema(description = "Page content")
        List<T> content,

        @Schema(description = "Current page number (0-based)", example = "0")
        int page,

        @Schema(description = "Page size", example = "20")
        int size,

        @Schema(description = "Total number of matching elements", example = "250")
        long totalElements,

        @Schema(description = "Total pages", example = "13")
        int totalPages,

        @Schema(description = "Whether this is the first page", example = "true")
        boolean first,

        @Schema(description = "Whether this is the last page", example = "false")
        boolean last
) {
    public static <T> PagedResponse<T> of(List<T> all, int page, int size) {
        long total = all.size();
        int totalPages = (int) Math.ceil((double) total / size);
        List<T> content = all.stream()
                .skip((long) page * size)
                .limit(size)
                .toList();
        return new PagedResponse<>(
                content, page, size, total, totalPages,
                page == 0,
                (long) (page + 1) * size >= total
        );
    }
}

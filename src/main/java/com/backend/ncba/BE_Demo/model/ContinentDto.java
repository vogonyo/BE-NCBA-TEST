package com.backend.ncba.BE_Demo.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Continent reference data")
public record ContinentDto(
        @Schema(description = "Continent code", example = "AF")
        String code,

        @Schema(description = "Continent name", example = "Africa")
        String name
) {
}

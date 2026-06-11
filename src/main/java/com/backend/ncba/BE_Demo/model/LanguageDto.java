package com.backend.ncba.BE_Demo.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Language reference data")
public record LanguageDto(
        @Schema(description = "ISO 639-2 language code", example = "SW")
        String isoCode,

        @Schema(description = "Language name", example = "Swahili")
        String name
) {
}

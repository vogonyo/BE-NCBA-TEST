package com.backend.ncba.BE_Demo.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Currency reference data")
public record CurrencyDto(
        @Schema(description = "ISO 4217 currency code", example = "KES")
        String isoCode,

        @Schema(description = "Currency name", example = "Kenyan Shilling")
        String name
) {
}

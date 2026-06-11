package com.backend.ncba.BE_Demo.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Full country information enriched with continent and currency names")
public record CountryDto(
        @Schema(description = "ISO 3166-1 alpha-2 country code", example = "KE")
        String isoCode,

        @Schema(description = "Country name", example = "Kenya")
        String name,

        @Schema(description = "Capital city", example = "Nairobi")
        String capitalCity,

        @Schema(description = "International dialing code", example = "254")
        String phoneCode,

        @Schema(description = "Continent ISO code", example = "AF")
        String continentCode,

        @Schema(description = "Continent name", example = "Africa")
        String continentName,

        @Schema(description = "ISO 4217 currency code", example = "KES")
        String currencyIsoCode,

        @Schema(description = "Currency name", example = "Kenyan Shilling")
        String currencyName,

        @Schema(description = "URL to the country flag image")
        String flagUrl,

        @Schema(description = "Official languages")
        List<LanguageDto> languages
) {
}

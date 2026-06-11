package com.backend.ncba.BE_Demo.controller;

import com.backend.ncba.BE_Demo.model.ApiResponse;
import com.backend.ncba.BE_Demo.model.CountryDto;
import com.backend.ncba.BE_Demo.model.PagedResponse;
import com.backend.ncba.BE_Demo.service.CountryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/countries")
@RequiredArgsConstructor
@Validated
@Tag(name = "Countries", description = "Country search, filtering, sorting and pagination")
public class CountryController {

    private final CountryService countryService;

    @GetMapping
    @Operation(summary = "Search and filter countries",
               description = "Returns a paginated, sorted list of countries. All filters are optional and combinable.")
    public ResponseEntity<ApiResponse<PagedResponse<CountryDto>>> getCountries(
            @Parameter(description = "Filter by country name (partial, case-insensitive)")
            @RequestParam(required = false) String name,

            @Parameter(description = "Filter by continent code (e.g. AF) or name")
            @RequestParam(required = false) String continent,

            @Parameter(description = "Filter by ISO 4217 currency code (e.g. USD)")
            @RequestParam(required = false) String currency,

            @Parameter(description = "Filter by language ISO code or name")
            @RequestParam(required = false) String language,

            @Parameter(description = "Page number, 0-based (default 0)")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must be >= 0") int page,

            @Parameter(description = "Page size, 1-100 (default 20)")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be >= 1")
            @Max(value = 100, message = "size must be <= 100") int size,

            @Parameter(description = "Sort field: name | isoCode | capitalCity | continentCode | " +
                                     "continentName | currencyIsoCode | currencyName | phoneCode")
            @RequestParam(defaultValue = "name") String sort,

            @Parameter(description = "Sort direction: ASC | DESC")
            @RequestParam(defaultValue = "ASC")
            @Pattern(regexp = "(?i)ASC|DESC", message = "direction must be ASC or DESC") String direction) {

        PagedResponse<CountryDto> result =
                countryService.getCountries(name, continent, currency, language, page, size, sort, direction);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{isoCode}")
    @Operation(summary = "Get a single country by ISO code",
               description = "Returns full details for the given ISO 3166-1 alpha-2 country code.")
    public ResponseEntity<ApiResponse<CountryDto>> getByIsoCode(
            @Parameter(description = "ISO 3166-1 alpha-2 code", example = "KE")
            @PathVariable String isoCode) {
        return ResponseEntity.ok(ApiResponse.ok(countryService.getCountryByIsoCode(isoCode)));
    }

    @GetMapping("/currency/{currencyCode}")
    @Operation(summary = "List countries sharing a currency",
               description = "Returns all countries that use the given ISO 4217 currency code.")
    public ResponseEntity<ApiResponse<List<CountryDto>>> getByCurrency(
            @Parameter(description = "ISO 4217 currency code", example = "USD")
            @PathVariable String currencyCode) {
        return ResponseEntity.ok(ApiResponse.ok(countryService.getCountriesByCurrency(currencyCode)));
    }
}

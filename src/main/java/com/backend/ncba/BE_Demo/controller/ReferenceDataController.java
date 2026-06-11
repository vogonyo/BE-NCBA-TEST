package com.backend.ncba.BE_Demo.controller;

import com.backend.ncba.BE_Demo.model.ApiResponse;
import com.backend.ncba.BE_Demo.model.ContinentDto;
import com.backend.ncba.BE_Demo.model.CurrencyDto;
import com.backend.ncba.BE_Demo.model.LanguageDto;
import com.backend.ncba.BE_Demo.service.CountryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Reference Data", description = "Continents, currencies and languages lookup endpoints")
public class ReferenceDataController {

    private final CountryService countryService;

    @GetMapping("/continents")
    @Operation(summary = "List all continents", description = "Returns all continents ordered by name.")
    public ResponseEntity<ApiResponse<List<ContinentDto>>> getContinents() {
        return ResponseEntity.ok(ApiResponse.ok(countryService.getContinents()));
    }

    @GetMapping("/currencies")
    @Operation(summary = "List all currencies", description = "Returns all currencies ordered by name.")
    public ResponseEntity<ApiResponse<List<CurrencyDto>>> getCurrencies() {
        return ResponseEntity.ok(ApiResponse.ok(countryService.getCurrencies()));
    }

    @GetMapping("/languages")
    @Operation(summary = "List all languages", description = "Returns all languages derived from country data, ordered by name.")
    public ResponseEntity<ApiResponse<List<LanguageDto>>> getLanguages() {
        return ResponseEntity.ok(ApiResponse.ok(countryService.getLanguages()));
    }
}

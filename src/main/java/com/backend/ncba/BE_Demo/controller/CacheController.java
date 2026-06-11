package com.backend.ncba.BE_Demo.controller;

import com.backend.ncba.BE_Demo.model.ApiResponse;
import com.backend.ncba.BE_Demo.model.CachedData;
import com.backend.ncba.BE_Demo.service.ReferenceDataCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cache")
@RequiredArgsConstructor
@Tag(name = "Cache Management", description = "Admin endpoints to inspect and refresh the data cache")
public class CacheController {

    private final ReferenceDataCache cache;

    @GetMapping("/status")
    @Operation(summary = "Cache status", description = "Returns current cache metadata including freshness.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        CachedData data = cache.getDataOrNull();
        Map<String, Object> info = new LinkedHashMap<>();

        if (data == null) {
            info.put("status", "EMPTY");
            info.put("detail", "Cache has not been populated yet");
        } else {
            Duration age = Duration.between(data.getLastUpdated(), Instant.now());
            info.put("status", cache.isHealthy() ? "HEALTHY" : "STALE");
            info.put("lastUpdated", data.getLastUpdated());
            info.put("cacheAgeMinutes", age.toMinutes());
            info.put("countries", data.getCountries().size());
            info.put("continents", data.getContinents().size());
            info.put("currencies", data.getCurrencies().size());
            info.put("languages", data.getLanguages().size());
        }

        return ResponseEntity.ok(ApiResponse.ok(info));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Force cache refresh",
               description = "Triggers an immediate SOAP fetch and cache update. Returns new cache status.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh() {
        cache.forceRefresh();
        CachedData data = cache.getData();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "REFRESHED");
        info.put("lastUpdated", data.getLastUpdated());
        info.put("countries", data.getCountries().size());
        info.put("continents", data.getContinents().size());
        info.put("currencies", data.getCurrencies().size());
        info.put("languages", data.getLanguages().size());
        return ResponseEntity.ok(ApiResponse.ok("Cache refreshed successfully", info));
    }
}

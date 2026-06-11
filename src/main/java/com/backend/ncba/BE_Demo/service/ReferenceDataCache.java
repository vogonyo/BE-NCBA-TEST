package com.backend.ncba.BE_Demo.service;

import com.backend.ncba.BE_Demo.client.CountryInfoSoapClient;
import com.backend.ncba.BE_Demo.exception.SoapServiceUnavailableException;
import com.backend.ncba.BE_Demo.model.CachedData;
import com.backend.ncba.BE_Demo.model.ContinentDto;
import com.backend.ncba.BE_Demo.model.CountryDto;
import com.backend.ncba.BE_Demo.model.CurrencyDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the in-memory reference data cache.
 *
 * Strategy:
 *  1. Populate at startup via @PostConstruct (synchronous – app is ready before first request).
 *  2. Refresh on a fixed schedule (default: every hour).
 *  3. On SOAP failure: log the error and retain the last known-good snapshot (stale-while-error).
 *  4. If no data has ever been loaded, throw {@link SoapServiceUnavailableException}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataCache {

    private final CountryInfoSoapClient soapClient;

    @Value("${rdas.cache.refresh-interval-ms:3600000}")
    private long refreshIntervalMs;

    /** Thread-safe reference to the current data snapshot. */
    private final AtomicReference<CachedData> cache = new AtomicReference<>();

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    @PostConstruct
    public void initialize() {
        log.info("Initializing RDAS reference data cache...");
        try {
            refresh();
        } catch (Exception ex) {
            log.error("Cache initialisation failed – service will retry on schedule. Cause: {}", ex.getMessage());
            // Do NOT rethrow: allow the application to start so Kubernetes liveness probes pass.
        }
    }

    @Scheduled(fixedDelayString = "${rdas.cache.refresh-interval-ms:3600000}")
    public void scheduledRefresh() {
        log.debug("Scheduled cache refresh triggered");
        try {
            refresh();
        } catch (Exception ex) {
            log.error("Scheduled cache refresh failed. Retaining stale data. Cause: {}", ex.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Data access
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns the current cached snapshot.
     * @throws SoapServiceUnavailableException if no data has ever been loaded.
     */
    public CachedData getData() {
        CachedData data = cache.get();
        if (data == null) {
            throw new SoapServiceUnavailableException(
                    "Reference data is not yet available – the upstream SOAP service may be unreachable. " +
                    "Please retry shortly.");
        }
        return data;
    }

    /** Returns the current snapshot without throwing (may be null). */
    public CachedData getDataOrNull() {
        return cache.get();
    }

    /** Returns true if the cache is populated and fresh (within 2× the refresh interval). */
    public boolean isHealthy() {
        CachedData data = cache.get();
        if (data == null) return false;
        return Duration.between(data.getLastUpdated(), Instant.now()).toMillis() < refreshIntervalMs * 2;
    }

    /** Forces an immediate cache refresh – used by the admin endpoint. */
    public CachedData forceRefresh() {
        refresh();
        return getData();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internal refresh
    // ──────────────────────────────────────────────────────────────────────────

    private void refresh() {
        log.info("Fetching reference data from SOAP service...");

        // 1. Continents and currencies first (small payloads, needed for country enrichment)
        List<ContinentDto> continents = soapClient.fetchContinents();
        List<CurrencyDto>  currencies = soapClient.fetchCurrencies();

        // 2. Full country list (single call, ~250 countries, enriched inline)
        List<CountryDto> countries = soapClient.fetchAllCountries(continents, currencies);

        cache.set(new CachedData(countries, continents, currencies));
        log.info("Cache refreshed: {} countries, {} continents, {} currencies loaded at {}",
                countries.size(), continents.size(), currencies.size(), Instant.now());
    }
}

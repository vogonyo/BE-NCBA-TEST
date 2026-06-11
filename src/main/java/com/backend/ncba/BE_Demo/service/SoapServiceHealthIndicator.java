package com.backend.ncba.BE_Demo.service;

import com.backend.ncba.BE_Demo.model.CachedData;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Exposes RDAS cache health via Spring Boot Actuator at /actuator/health.
 */
@Component("rdas")
@RequiredArgsConstructor
public class SoapServiceHealthIndicator implements HealthIndicator {

    private final ReferenceDataCache cache;

    @Override
    public Health health() {
        CachedData data = cache.getDataOrNull();

        if (data == null) {
            return Health.down()
                    .withDetail("reason", "Reference data has not been loaded yet")
                    .build();
        }

        Duration age = Duration.between(data.getLastUpdated(), Instant.now());

        if (!cache.isHealthy()) {
            return Health.down()
                    .withDetail("reason", "Cache is stale – SOAP service may be unreachable")
                    .withDetail("cacheAgeMinutes", age.toMinutes())
                    .withDetail("lastUpdated", data.getLastUpdated())
                    .build();
        }

        return Health.up()
                .withDetail("countries", data.getCountries().size())
                .withDetail("continents", data.getContinents().size())
                .withDetail("currencies", data.getCurrencies().size())
                .withDetail("languages", data.getLanguages().size())
                .withDetail("cacheAgeMinutes", age.toMinutes())
                .withDetail("lastUpdated", data.getLastUpdated())
                .build();
    }
}

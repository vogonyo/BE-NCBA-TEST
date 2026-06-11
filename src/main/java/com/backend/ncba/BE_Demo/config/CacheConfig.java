package com.backend.ncba.BE_Demo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("countries", "continents", "currencies", "languages");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.HOURS)
                .maximumSize(500)
                .recordStats());
        return manager;
    }
}

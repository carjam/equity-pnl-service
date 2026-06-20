package com.companyx.equity.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for caching corporate actions data.
 * 
 * Corporate actions are relatively static and can be cached aggressively:
 * - Historical events (>30 days old): Cache for 7 days
 * - Recent events: Cache for 24 hours
 */
@Configuration
@EnableCaching
public class CorporateActionCacheConfig {
    
    @Bean
    public CacheManager corporateActionCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "corporate-actions-dividends",
                "corporate-actions-splits",
                "corporate-actions-mergers",
                "corporate-actions-spinoffs",
                "corporate-actions-symbol-changes",
                "corporate-actions-delistings"
        );
        
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }
    
    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(24, TimeUnit.HOURS) // 24 hour TTL for recent data
                .recordStats();
    }
}

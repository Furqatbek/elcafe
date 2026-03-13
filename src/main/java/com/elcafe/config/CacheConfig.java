package com.elcafe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for caching analytics and other expensive operations.
 * Uses Redis when available, falls back to in-memory caching.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    // Cache names
    public static final String ANALYTICS_SUMMARY = "analytics-summary";
    public static final String DAILY_REVENUE = "daily-revenue";
    public static final String CUSTOMER_LTV = "customer-ltv";
    public static final String CUSTOMER_RETENTION = "customer-retention";
    public static final String SALES_BY_CATEGORY = "sales-by-category";
    public static final String COGS_ANALYTICS = "cogs-analytics";
    public static final String PROFITABILITY = "profitability";
    public static final String CONTRIBUTION_MARGINS = "contribution-margins";
    public static final String INVENTORY_TURNOVER = "inventory-turnover";
    public static final String PEAK_HOURS = "peak-hours";

    /**
     * Redis cache manager for distributed caching.
     * Primary cache manager when Redis is available.
     */
    @Bean
    @Primary
    public CacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory,
            @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper) {
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper);
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Analytics summary - 10 minute TTL (frequently changing)
        cacheConfigurations.put(ANALYTICS_SUMMARY,
                defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // Daily revenue - 30 minute TTL (historical data, less frequent updates)
        cacheConfigurations.put(DAILY_REVENUE,
                defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // Customer LTV - 1 hour TTL (expensive calculation, changes slowly)
        cacheConfigurations.put(CUSTOMER_LTV,
                defaultConfig.entryTtl(Duration.ofHours(1)));

        // Customer retention - 1 hour TTL
        cacheConfigurations.put(CUSTOMER_RETENTION,
                defaultConfig.entryTtl(Duration.ofHours(1)));

        // Sales by category - 15 minute TTL
        cacheConfigurations.put(SALES_BY_CATEGORY,
                defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // COGS analytics - 30 minute TTL
        cacheConfigurations.put(COGS_ANALYTICS,
                defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // Profitability - 30 minute TTL
        cacheConfigurations.put(PROFITABILITY,
                defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // Contribution margins - 30 minute TTL
        cacheConfigurations.put(CONTRIBUTION_MARGINS,
                defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // Inventory turnover - 1 hour TTL
        cacheConfigurations.put(INVENTORY_TURNOVER,
                defaultConfig.entryTtl(Duration.ofHours(1)));

        // Peak hours - 30 minute TTL
        cacheConfigurations.put(PEAK_HOURS,
                defaultConfig.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * Fallback in-memory cache manager.
     * Used when Redis is not available.
     */
    @Bean(name = "simpleCacheManager")
    public CacheManager simpleCacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
                new ConcurrentMapCache(ANALYTICS_SUMMARY),
                new ConcurrentMapCache(DAILY_REVENUE),
                new ConcurrentMapCache(CUSTOMER_LTV),
                new ConcurrentMapCache(CUSTOMER_RETENTION),
                new ConcurrentMapCache(SALES_BY_CATEGORY),
                new ConcurrentMapCache(COGS_ANALYTICS),
                new ConcurrentMapCache(PROFITABILITY),
                new ConcurrentMapCache(CONTRIBUTION_MARGINS),
                new ConcurrentMapCache(INVENTORY_TURNOVER),
                new ConcurrentMapCache(PEAK_HOURS)
        ));
        return cacheManager;
    }
}

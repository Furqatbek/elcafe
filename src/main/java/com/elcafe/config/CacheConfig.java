package com.elcafe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.interceptor.CacheErrorHandler;
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
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /**
     * Silently absorbs Redis errors (e.g. READONLY replica, connection refused) so
     * that a Redis failure never crashes a user request — the method is simply
     * invoked directly without caching for that call.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache GET error on cache '{}' key '{}': {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis cache PUT error on cache '{}' key '{}': {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache EVICT error on cache '{}' key '{}': {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis cache CLEAR error on cache '{}': {}", cache.getName(), exception.getMessage());
            }
        };
    }

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
    private ObjectMapper buildRedisObjectMapper() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL);
        return mapper;
    }

    @Bean
    @Primary
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(buildRedisObjectMapper());
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

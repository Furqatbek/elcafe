package com.elcafe.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for API rate limiting using Bucket4j.
 * Provides per-user and per-endpoint rate limiting to protect expensive operations.
 */
@Slf4j
@Component
public class RateLimitConfig {

    // Rate limit buckets per user (keyed by username)
    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();

    // Rate limit buckets per endpoint (keyed by endpoint name)
    private final Map<String, Bucket> endpointBuckets = new ConcurrentHashMap<>();

    // Strict buckets for unauthenticated auth endpoints, keyed by endpoint + client IP (brute-force
    // defense). Kept in a separate map so it can be swept without touching the per-user limits.
    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();

    /**
     * Consume a token from a per-IP auth bucket (login / PIN / OTP / password-reset). Strict: 10 requests
     * per minute per key. Returns true if allowed, false if the caller should be throttled (429).
     */
    public boolean tryConsumeAuth(String key) {
        Bucket bucket = authBuckets.computeIfAbsent(key, k -> createAuthBucket());
        boolean consumed = bucket.tryConsume(1);
        if (!consumed) {
            log.warn("Auth rate limit exceeded for key {}", key);
        }
        return consumed;
    }

    private Bucket createAuthBucket() {
        // 10 attempts/minute per IP+endpoint — comfortable for a human, hostile to a brute-forcer.
        Bandwidth limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Evict auth buckets periodically so an attacker rotating source IPs can't grow the map without
     * bound. Clearing only resets rate windows (worst case a fresh 1-minute window), and per-account
     * lockout (LoginAttemptService) is unaffected, so this is safe.
     */
    @Scheduled(fixedRate = 3_600_000) // hourly
    void evictAuthBuckets() {
        int size = authBuckets.size();
        if (size > 0) {
            authBuckets.clear();
            log.debug("Cleared {} auth rate-limit buckets", size);
        }
    }

    /**
     * Get or create a rate limit bucket for a user.
     * Default: 100 requests per minute.
     */
    public Bucket getUserBucket(String username) {
        return userBuckets.computeIfAbsent(username, k -> createUserBucket());
    }

    /**
     * Get or create a rate limit bucket for expensive analytics endpoints.
     * More restrictive: 20 requests per minute.
     */
    public Bucket getAnalyticsBucket(String username) {
        String key = "analytics:" + username;
        return userBuckets.computeIfAbsent(key, k -> createAnalyticsBucket());
    }

    /**
     * Get a global rate limit bucket for a specific expensive endpoint.
     * Protects against expensive queries overwhelming the database.
     */
    public Bucket getExpensiveEndpointBucket(String endpointName) {
        return endpointBuckets.computeIfAbsent(endpointName, k -> createExpensiveEndpointBucket());
    }

    /**
     * Consume a token from the user's analytics bucket.
     * Returns true if allowed, false if rate limited.
     */
    public boolean tryConsumeAnalytics(String username) {
        Bucket bucket = getAnalyticsBucket(username);
        boolean consumed = bucket.tryConsume(1);
        if (!consumed) {
            log.warn("Rate limit exceeded for user {} on analytics endpoints", username);
        }
        return consumed;
    }

    /**
     * Consume a token from an expensive endpoint bucket.
     * Returns true if allowed, false if rate limited.
     */
    public boolean tryConsumeExpensiveEndpoint(String endpointName) {
        Bucket bucket = getExpensiveEndpointBucket(endpointName);
        boolean consumed = bucket.tryConsume(1);
        if (!consumed) {
            log.warn("Rate limit exceeded for expensive endpoint: {}", endpointName);
        }
        return consumed;
    }

    private Bucket createUserBucket() {
        // 100 requests per minute with gradual refill
        Bandwidth limit = Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createAnalyticsBucket() {
        // 20 requests per minute for analytics (more restrictive)
        Bandwidth limit = Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createExpensiveEndpointBucket() {
        // 10 requests per minute for expensive endpoints (most restrictive)
        // Plus a burst capacity of 5 extra requests
        Bandwidth burstLimit = Bandwidth.classic(15, Refill.intervally(10, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(burstLimit).build();
    }

    /**
     * Get remaining tokens for a user's analytics bucket.
     */
    public long getRemainingAnalyticsTokens(String username) {
        Bucket bucket = getAnalyticsBucket(username);
        return bucket.getAvailableTokens();
    }

    /**
     * Clear expired buckets (call periodically for cleanup).
     */
    public void cleanupExpiredBuckets() {
        // In production, you'd implement TTL-based cleanup
        // For now, we rely on ConcurrentHashMap's bounded size
        log.debug("Bucket cleanup - current user buckets: {}, endpoint buckets: {}",
                  userBuckets.size(), endpointBuckets.size());
    }
}

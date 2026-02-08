package com.elcafe.modules.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service to ensure idempotent payment processing.
 * Prevents duplicate payments from race conditions or client retries.
 * <p>
 * Uses Redis for distributed locking when available, falls back to
 * in-memory locking for single-instance deployments.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentIdempotencyService {

    private final StringRedisTemplate redisTemplate;

    // Fallback for when Redis is unavailable
    private final ConcurrentMap<String, Long> inMemoryLocks = new ConcurrentHashMap<>();

    private static final Duration LOCK_DURATION = Duration.ofMinutes(5);
    private static final String PAYMENT_LOCK_PREFIX = "payment:lock:";
    private static final String IDEMPOTENCY_PREFIX = "payment:idempotency:";

    /**
     * Acquires a lock for processing a payment on an order.
     * This prevents multiple concurrent payment attempts for the same order.
     *
     * @param orderId The order ID
     * @return true if lock acquired, false if another payment is in progress
     */
    public boolean acquireOrderPaymentLock(Long orderId) {
        String lockKey = PAYMENT_LOCK_PREFIX + orderId;

        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, String.valueOf(System.currentTimeMillis()), LOCK_DURATION);
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Acquired payment lock for order {}", orderId);
                return true;
            }
            log.warn("Failed to acquire payment lock for order {} - payment in progress", orderId);
            return false;
        } catch (Exception e) {
            log.warn("Redis unavailable for payment lock, using in-memory: {}", e.getMessage());
            return acquireInMemoryLock(lockKey);
        }
    }

    /**
     * Releases the payment processing lock for an order.
     *
     * @param orderId The order ID
     */
    public void releaseOrderPaymentLock(Long orderId) {
        String lockKey = PAYMENT_LOCK_PREFIX + orderId;

        try {
            redisTemplate.delete(lockKey);
            log.debug("Released payment lock for order {}", orderId);
        } catch (Exception e) {
            log.warn("Redis unavailable for releasing lock, using in-memory: {}", e.getMessage());
            releaseInMemoryLock(lockKey);
        }
    }

    /**
     * Checks if a payment request is a duplicate using an idempotency key.
     *
     * @param idempotencyKey Unique key for the payment request (typically from client)
     * @return true if this is a new request, false if duplicate
     */
    public boolean isNewPaymentRequest(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return true; // No idempotency key means treat as new
        }

        String key = IDEMPOTENCY_PREFIX + idempotencyKey;

        try {
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(key, String.valueOf(System.currentTimeMillis()), Duration.ofHours(24));
            if (Boolean.TRUE.equals(isNew)) {
                log.debug("New payment request with idempotency key: {}", idempotencyKey);
                return true;
            }
            log.warn("Duplicate payment request detected with idempotency key: {}", idempotencyKey);
            return false;
        } catch (Exception e) {
            log.warn("Redis unavailable for idempotency check, treating as new: {}", e.getMessage());
            return true; // When Redis is down, allow the request but rely on DB constraints
        }
    }

    /**
     * Registers a successful payment's transaction ID to prevent future duplicates.
     *
     * @param transactionId The payment transaction ID
     * @param orderId The order ID
     */
    public void registerSuccessfulPayment(String transactionId, Long orderId) {
        if (transactionId == null) return;

        String key = "payment:txn:" + transactionId;

        try {
            redisTemplate.opsForValue().set(key, orderId.toString(), Duration.ofHours(24));
            log.debug("Registered successful payment: txn={}, order={}", transactionId, orderId);
        } catch (Exception e) {
            log.warn("Failed to register payment in Redis: {}", e.getMessage());
            // Not critical - DB constraint will catch duplicates
        }
    }

    /**
     * Checks if a transaction ID has already been processed.
     *
     * @param transactionId The transaction ID to check
     * @return The order ID if already processed, null otherwise
     */
    public Long getProcessedOrderForTransaction(String transactionId) {
        if (transactionId == null) return null;

        String key = "payment:txn:" + transactionId;

        try {
            String orderId = redisTemplate.opsForValue().get(key);
            return orderId != null ? Long.parseLong(orderId) : null;
        } catch (Exception e) {
            log.warn("Failed to check transaction in Redis: {}", e.getMessage());
            return null;
        }
    }

    // In-memory fallback methods

    private boolean acquireInMemoryLock(String lockKey) {
        long now = System.currentTimeMillis();
        Long existing = inMemoryLocks.putIfAbsent(lockKey, now);

        if (existing == null) {
            return true;
        }

        // Check if existing lock has expired
        if (now - existing > LOCK_DURATION.toMillis()) {
            if (inMemoryLocks.replace(lockKey, existing, now)) {
                return true;
            }
        }

        return false;
    }

    private void releaseInMemoryLock(String lockKey) {
        inMemoryLocks.remove(lockKey);
    }
}

package com.elcafe.modules.order.service;

import com.elcafe.modules.order.entity.IdempotencyKey;
import com.elcafe.modules.order.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Service for handling idempotent operations.
 * Prevents duplicate operations (like double-charging) by tracking unique request keys.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    private static final int LOCK_TIMEOUT_SECONDS = 30;

    /**
     * Execute an operation idempotently.
     * If the same idempotency key was used before, returns the cached result.
     *
     * @param idempotencyKey Unique key for this operation
     * @param operationType  Type of operation (e.g., "PAYMENT", "REFUND")
     * @param request        The request object (used for hash verification)
     * @param operation      The operation to execute
     * @param resultClass    The class of the result
     * @return The result of the operation (either from cache or freshly executed)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> IdempotentResult<T> executeIdempotently(
            String idempotencyKey,
            String operationType,
            Object request,
            Supplier<T> operation,
            Class<T> resultClass) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            // No idempotency key provided - execute normally
            T result = operation.get();
            return IdempotentResult.newResult(result);
        }

        String requestHash = hashRequest(request);

        // Check for existing key
        Optional<IdempotencyKey> existingKey = idempotencyKeyRepository
                .findByKeyAndOperationType(idempotencyKey, operationType);

        if (existingKey.isPresent()) {
            IdempotencyKey key = existingKey.get();

            // Check if expired
            if (key.isExpired()) {
                log.info("Idempotency key {} expired, allowing re-execution", idempotencyKey);
                idempotencyKeyRepository.delete(key);
            } else if (key.isLocked()) {
                // Operation in progress
                throw new IdempotencyConflictException(
                        "Operation with this idempotency key is already in progress");
            } else if (key.getResponseBody() != null) {
                // Return cached response
                log.info("Returning cached response for idempotency key: {}", idempotencyKey);

                // Verify request hash matches
                if (requestHash != null && !requestHash.equals(key.getRequestHash())) {
                    throw new IdempotencyConflictException(
                            "Idempotency key already used with different request parameters");
                }

                try {
                    T cachedResult = objectMapper.readValue(key.getResponseBody(), resultClass);
                    return IdempotentResult.cachedResult(cachedResult, key.getResultId());
                } catch (Exception e) {
                    log.error("Failed to deserialize cached response, re-executing", e);
                }
            }
        }

        // Create new idempotency key with lock
        IdempotencyKey newKey = IdempotencyKey.builder()
                .key(idempotencyKey)
                .operationType(operationType)
                .requestHash(requestHash)
                .lockedUntil(LocalDateTime.now().plusSeconds(LOCK_TIMEOUT_SECONDS))
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        idempotencyKeyRepository.save(newKey);

        try {
            // Execute the operation
            T result = operation.get();

            // Store the result
            newKey.setResponseStatus("SUCCESS");
            newKey.setResponseBody(objectMapper.writeValueAsString(result));
            newKey.setLockedUntil(null);

            // Extract result ID if available
            if (result != null) {
                try {
                    var idField = result.getClass().getDeclaredField("id");
                    idField.setAccessible(true);
                    Object idValue = idField.get(result);
                    if (idValue instanceof Long) {
                        newKey.setResultId((Long) idValue);
                    }
                } catch (NoSuchFieldException ignored) {
                    // Result doesn't have an id field
                }
            }

            idempotencyKeyRepository.save(newKey);

            return IdempotentResult.newResult(result);

        } catch (Exception e) {
            // Store failure
            newKey.setResponseStatus("FAILED");
            newKey.setLockedUntil(null);
            idempotencyKeyRepository.save(newKey);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    /**
     * Check if an idempotency key exists and is valid
     */
    @Transactional(readOnly = true)
    public boolean keyExists(String idempotencyKey) {
        return idempotencyKeyRepository.existsByKeyAndNotExpired(idempotencyKey, LocalDateTime.now());
    }

    /**
     * Get cached result for an idempotency key
     */
    @Transactional(readOnly = true)
    public <T> Optional<T> getCachedResult(String idempotencyKey, String operationType, Class<T> resultClass) {
        return idempotencyKeyRepository.findByKeyAndOperationType(idempotencyKey, operationType)
                .filter(key -> !key.isExpired())
                .filter(key -> key.getResponseBody() != null)
                .map(key -> {
                    try {
                        return objectMapper.readValue(key.getResponseBody(), resultClass);
                    } catch (Exception e) {
                        log.error("Failed to deserialize cached result", e);
                        return null;
                    }
                });
    }

    /**
     * Clean up expired idempotency keys (scheduled task)
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void cleanupExpiredKeys() {
        int deleted = idempotencyKeyRepository.deleteExpiredKeys(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired idempotency keys", deleted);
        }
    }

    private String hashRequest(Object request) {
        if (request == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.warn("Failed to hash request", e);
            return null;
        }
    }

    /**
     * Result wrapper that indicates whether the result is from cache or newly executed
     */
    public record IdempotentResult<T>(T result, boolean fromCache, Long cachedResultId) {
        public static <T> IdempotentResult<T> newResult(T result) {
            return new IdempotentResult<>(result, false, null);
        }

        public static <T> IdempotentResult<T> cachedResult(T result, Long resultId) {
            return new IdempotentResult<>(result, true, resultId);
        }
    }

    /**
     * Exception thrown when there's an idempotency conflict
     */
    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) {
            super(message);
        }
    }
}

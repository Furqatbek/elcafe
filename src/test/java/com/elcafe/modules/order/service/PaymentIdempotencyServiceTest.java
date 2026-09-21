package com.elcafe.modules.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentIdempotencyServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @InjectMocks private PaymentIdempotencyService idempotencyService;

    @Test
    @DisplayName("acquireOrderPaymentLock — success when Redis returns true")
    void acquireLock_success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertTrue(idempotencyService.acquireOrderPaymentLock(1L));
    }

    @Test
    @DisplayName("acquireOrderPaymentLock — fails when already locked")
    void acquireLock_alreadyLocked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertFalse(idempotencyService.acquireOrderPaymentLock(1L));
    }

    @Test
    @DisplayName("acquireOrderPaymentLock — falls back to in-memory when Redis down")
    void acquireLock_redisFallback() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        // First call should succeed (in-memory)
        assertTrue(idempotencyService.acquireOrderPaymentLock(1L));
    }

    @Test
    @DisplayName("releaseOrderPaymentLock — deletes Redis key")
    void releaseLock_deletesKey() {
        idempotencyService.releaseOrderPaymentLock(1L);
        verify(redisTemplate).delete("payment:lock:1");
    }

    @Test
    @DisplayName("isNewPaymentRequest — null key treated as new")
    void isNewRequest_nullKey_isNew() {
        assertTrue(idempotencyService.isNewPaymentRequest(null));
    }

    @Test
    @DisplayName("isNewPaymentRequest — new key returns true")
    void isNewRequest_newKey_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertTrue(idempotencyService.isNewPaymentRequest("KEY-1"));
    }

    @Test
    @DisplayName("isNewPaymentRequest — duplicate key returns false")
    void isNewRequest_duplicate_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertFalse(idempotencyService.isNewPaymentRequest("KEY-1"));
    }

    @Test
    @DisplayName("getProcessedOrderForTransaction — returns orderId")
    void getProcessedOrder_returnsOrderId() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("payment:txn:TXN-001")).thenReturn("42");

        assertEquals(42L, idempotencyService.getProcessedOrderForTransaction("TXN-001"));
    }

    @Test
    @DisplayName("getProcessedOrderForTransaction — null txnId returns null")
    void getProcessedOrder_nullTxn_returnsNull() {
        assertNull(idempotencyService.getProcessedOrderForTransaction(null));
    }

    @Test
    @DisplayName("registerSuccessfulPayment — stores in Redis")
    void registerPayment_storesInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        idempotencyService.registerSuccessfulPayment("TXN-001", 42L);

        verify(valueOps).set(eq("payment:txn:TXN-001"), eq("42"), any(Duration.class));
    }
}

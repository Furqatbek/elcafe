package com.elcafe.modules.order.service;

import com.elcafe.modules.order.entity.IdempotencyKey;
import com.elcafe.modules.order.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock private IdempotencyKeyRepository idempotencyKeyRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private IdempotencyService idempotencyService;

    @Test
    @DisplayName("executeIdempotently — null key executes normally")
    void nullKey_executesNormally() {
        IdempotencyService.IdempotentResult<String> result =
                idempotencyService.executeIdempotently(null, "TEST", null, () -> "hello", String.class);

        assertFalse(result.fromCache());
        assertEquals("hello", result.result());
    }

    @Test
    @DisplayName("executeIdempotently — new key executes and stores result")
    void newKey_executesAndStores() {
        when(idempotencyKeyRepository.findByKeyAndOperationType("KEY-1", "PAYMENT"))
                .thenReturn(Optional.empty());
        when(idempotencyKeyRepository.save(any(IdempotencyKey.class)))
                .thenAnswer(i -> i.getArgument(0));

        IdempotencyService.IdempotentResult<String> result =
                idempotencyService.executeIdempotently("KEY-1", "PAYMENT", null, () -> "done", String.class);

        assertFalse(result.fromCache());
        assertEquals("done", result.result());
    }

    @Test
    @DisplayName("executeIdempotently — locked key throws conflict")
    void lockedKey_throwsConflict() {
        IdempotencyKey locked = IdempotencyKey.builder()
                .key("KEY-1").operationType("PAYMENT")
                .lockedUntil(LocalDateTime.now().plusMinutes(5))
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        when(idempotencyKeyRepository.findByKeyAndOperationType("KEY-1", "PAYMENT"))
                .thenReturn(Optional.of(locked));

        assertThrows(IdempotencyService.IdempotencyConflictException.class,
                () -> idempotencyService.executeIdempotently(
                        "KEY-1", "PAYMENT", null, () -> "test", String.class));
    }

    @Test
    @DisplayName("keyExists — returns true for non-expired key")
    void keyExists_nonExpired_returnsTrue() {
        when(idempotencyKeyRepository.existsByKeyAndNotExpired(eq("KEY-1"), any(LocalDateTime.class)))
                .thenReturn(true);

        assertTrue(idempotencyService.keyExists("KEY-1"));
    }

    @Test
    @DisplayName("keyExists — returns false for expired/missing key")
    void keyExists_expired_returnsFalse() {
        when(idempotencyKeyRepository.existsByKeyAndNotExpired(eq("KEY-1"), any(LocalDateTime.class)))
                .thenReturn(false);

        assertFalse(idempotencyService.keyExists("KEY-1"));
    }

    @Test
    @DisplayName("cleanupExpiredKeys — deletes expired keys")
    void cleanup_deletesExpired() {
        when(idempotencyKeyRepository.deleteExpiredKeys(any(LocalDateTime.class))).thenReturn(5);

        idempotencyService.cleanupExpiredKeys();

        verify(idempotencyKeyRepository).deleteExpiredKeys(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("executeIdempotently — failure stores FAILED status")
    void failure_storesFailedStatus() {
        when(idempotencyKeyRepository.findByKeyAndOperationType("KEY-1", "PAYMENT"))
                .thenReturn(Optional.empty());
        when(idempotencyKeyRepository.save(any(IdempotencyKey.class)))
                .thenAnswer(i -> i.getArgument(0));

        assertThrows(RuntimeException.class,
                () -> idempotencyService.executeIdempotently(
                        "KEY-1", "PAYMENT", null,
                        () -> { throw new RuntimeException("DB error"); },
                        String.class));

        // Verify the key was saved with FAILED status (second save call)
        ArgumentCaptor<IdempotencyKey> captor = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(idempotencyKeyRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());

        IdempotencyKey lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("FAILED", lastSaved.getResponseStatus());
    }
}

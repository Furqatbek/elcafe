package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.entity.InstagramProcessedEvent;
import com.elcafe.modules.instagram.repository.InstagramProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit contract of the dedup gate, with the repository mocked so every branch — including the
 * concurrent-redelivery race that only the unique constraint can resolve — is exercised
 * deterministically (a real race is not reproducible single-threaded).
 */
@ExtendWith(MockitoExtension.class)
class InstagramWebhookDedupServiceTest {

    private static final long TENANT = 5L;

    @Mock private InstagramProcessedEventRepository repository;
    @InjectMocks private InstagramWebhookDedupService service;

    @BeforeEach
    void setRetention() {
        ReflectionTestUtils.setField(service, "retentionDays", 7L);
    }

    @Test
    @DisplayName("first delivery records the event and returns true")
    void firstDeliveryRecordsAndReturnsTrue() {
        when(repository.existsByRestaurantIdAndEventId(TENANT, "msg:a")).thenReturn(false);

        assertThat(service.firstDelivery(TENANT, "msg:a")).isTrue();
        verify(repository).save(any(InstagramProcessedEvent.class));
    }

    @Test
    @DisplayName("a known event id is a duplicate — returns false, records nothing")
    void knownEventIsADuplicate() {
        when(repository.existsByRestaurantIdAndEventId(TENANT, "msg:a")).thenReturn(true);

        assertThat(service.firstDelivery(TENANT, "msg:a")).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a null or blank id cannot be deduplicated — processed, and the table is untouched")
    void nullOrBlankIdFailsOpen() {
        assertThat(service.firstDelivery(TENANT, null)).isTrue();
        assertThat(service.firstDelivery(TENANT, "  ")).isTrue();

        verify(repository, never()).existsByRestaurantIdAndEventId(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a race lost on the unique constraint is treated as a duplicate, not an error")
    void concurrentInsertRaceIsADuplicate() {
        // existsBy said "new", but a concurrent re-delivery inserted first — save hits the constraint.
        when(repository.existsByRestaurantIdAndEventId(TENANT, "msg:a")).thenReturn(false);
        when(repository.save(any(InstagramProcessedEvent.class)))
                .thenThrow(new DataIntegrityViolationException("uq_ig_processed_event"));

        assertThat(service.firstDelivery(TENANT, "msg:a")).isFalse();
    }

    @Test
    @DisplayName("the retention sweep deletes by a cutoff derived from retentionDays")
    void purgeDeletesOlderThanRetention() {
        when(repository.deleteByProcessedAtBefore(any())).thenReturn(3);

        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
        service.purgeOldEvents();
        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deleteByProcessedAtBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, after);
    }
}

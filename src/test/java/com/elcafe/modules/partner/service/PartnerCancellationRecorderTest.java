package com.elcafe.modules.partner.service;

import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The ticket a venue is owed for, at the level of one write.
 *
 * <p>Whether it survives the refusal that raises it is the integration test's subject. What is worth
 * pinning here is narrower and easier to get wrong: a reason long enough to overflow its column must
 * not take the record down with it, and a repeat must be recognisable to the caller.
 */
@ExtendWith(MockitoExtension.class)
class PartnerCancellationRecorderTest {

    @Mock private OrderRepository orderRepository;

    @InjectMocks private PartnerCancellationRecorder recorder;

    @Test
    @DisplayName("a first refusal is recorded, and says so")
    void firstRefusal_isRecorded() {
        when(orderRepository.recordPartnerCancelRefused(anyLong(), any(), any(), any())).thenReturn(1);

        assertThat(recorder.recordRefusal(7L, "zbr", OrderStatus.PREPARING, "Customer cancelled"))
                .isTrue();

        verify(orderRepository).recordPartnerCancelRefused(
                eq(7L), any(OffsetDateTime.class), eq(OrderStatus.PREPARING), eq("Customer cancelled"));
    }

    @Test
    @DisplayName("a repeat changes nothing and is reported as a repeat")
    void repeatedRefusal_isNotRecordedAgain() {
        // The conditional update matched no row, which is what a second delivery of the same webhook
        // looks like. The venue is owed for one ticket, not one per retry.
        when(orderRepository.recordPartnerCancelRefused(anyLong(), any(), any(), any())).thenReturn(0);

        assertThat(recorder.recordRefusal(7L, "zbr", OrderStatus.READY, "Same message again"))
                .isFalse();
    }

    @Test
    @DisplayName("a reason too long for the column is trimmed rather than lost")
    void overlongReason_isTruncated() {
        // A partner's reason field is theirs, not ours, and nothing stops it carrying a paragraph.
        // Letting it overflow would throw at the insert and cost us the whole record — which is the
        // one thing this class exists to guarantee.
        when(orderRepository.recordPartnerCancelRefused(anyLong(), any(), any(), any())).thenReturn(1);
        String essay = "x".repeat(900);

        recorder.recordRefusal(7L, "zbr", OrderStatus.PREPARING, essay);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(orderRepository).recordPartnerCancelRefused(
                anyLong(), any(OffsetDateTime.class), any(OrderStatus.class), reason.capture());
        assertThat(reason.getValue()).hasSize(500).startsWith("xxx");
    }

    @Test
    @DisplayName("no reason at all is fine — the refusal still counts")
    void missingReason_isStillRecorded() {
        when(orderRepository.recordPartnerCancelRefused(anyLong(), any(), any(), any())).thenReturn(1);

        assertThat(recorder.recordRefusal(7L, "zbr", OrderStatus.PREPARING, null)).isTrue();

        verify(orderRepository).recordPartnerCancelRefused(
                eq(7L), any(OffsetDateTime.class), eq(OrderStatus.PREPARING), eq(null));
    }
}

package com.elcafe.modules.partner.outbox;

import com.elcafe.modules.partner.entity.IntegrationEvent;
import com.elcafe.modules.partner.enums.IntegrationEventStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The retry policy, in isolation: how long we wait, how long we keep trying, and when we stop.
 *
 * <p>Worth pinning precisely because both failure modes are expensive and invisible. Backing off too
 * little turns our retries into a denial-of-service against a partner already having an incident;
 * giving up too eagerly means a menu update silently never arrives.
 */
class IntegrationEventTest {

    private IntegrationEvent event(int maxAttempts) {
        return IntegrationEvent.builder()
                .partnerId(7L).restaurantId(3L)
                .subjectKey("order:1")
                .payload("{}")
                .maxAttempts(maxAttempts)
                .build();
    }

    @Test
    @DisplayName("a delivered event is terminal and clears the last error")
    void markSent_isTerminal() {
        IntegrationEvent e = event(10);
        e.markAttemptFailed("temporary blip");

        e.markSent();

        assertThat(e.getStatus()).isEqualTo(IntegrationEventStatus.SENT);
        assertThat(e.getDispatchedAt()).isNotNull();
        // Leaving a stale error on a delivered event makes the queue look broken when it is not.
        assertThat(e.getLastError()).isNull();
    }

    @Test
    @DisplayName("backoff grows with each attempt rather than hammering a struggling partner")
    void backoff_grows() {
        IntegrationEvent e = event(10);

        e.markAttemptFailed("boom");
        Duration first = Duration.between(OffsetDateTime.now(), e.getNextAttemptAt());

        e.markAttemptFailed("boom");
        Duration second = Duration.between(OffsetDateTime.now(), e.getNextAttemptAt());

        assertThat(e.getStatus()).isEqualTo(IntegrationEventStatus.PENDING);
        assertThat(second).isGreaterThan(first);
    }

    @Test
    @DisplayName("backoff stops growing at 30 minutes")
    void backoff_isCapped() {
        IntegrationEvent e = event(100);
        for (int i = 0; i < 30; i++) {
            e.markAttemptFailed("still down");
        }

        // Unbounded doubling would push the next attempt past the end of the week; a partner's
        // incident is measured in hours.
        assertThat(Duration.between(OffsetDateTime.now(), e.getNextAttemptAt()))
                .isLessThanOrEqualTo(Duration.ofMinutes(31));
    }

    @Test
    @DisplayName("the event dead-letters once its attempts are spent")
    void deadLetters_afterMaxAttempts() {
        IntegrationEvent e = event(3);

        e.markAttemptFailed("1");
        assertThat(e.getStatus()).isEqualTo(IntegrationEventStatus.PENDING);
        e.markAttemptFailed("2");
        assertThat(e.getStatus()).isEqualTo(IntegrationEventStatus.PENDING);
        e.markAttemptFailed("3");

        assertThat(e.getStatus()).isEqualTo(IntegrationEventStatus.DEAD_LETTER);
        assertThat(e.getDeadLetteredAt()).isNotNull();
        assertThat(e.getLastError()).isEqualTo("3");
    }

    @Test
    @DisplayName("requeue puts a dead letter back in play with a clean slate")
    void requeue_resetsAttempts() {
        IntegrationEvent e = event(1);
        e.markAttemptFailed("partner was down");
        assertThat(e.getStatus()).isEqualTo(IntegrationEventStatus.DEAD_LETTER);

        e.requeue();

        // The operator pressing this means "they are back" — carrying the old attempt count over would
        // dead-letter it again on the first hiccup.
        assertThat(e.getStatus()).isEqualTo(IntegrationEventStatus.PENDING);
        assertThat(e.getAttemptCount()).isZero();
        assertThat(e.getDeadLetteredAt()).isNull();
    }

    @Test
    @DisplayName("an enormous error body is truncated to fit the column")
    void error_isTruncated() {
        IntegrationEvent e = event(10);

        // Partners return HTML error pages; the column is 1000 chars and exists to be read by a human.
        e.markAttemptFailed("x".repeat(5000));

        assertThat(e.getLastError()).hasSize(1000);
    }

    @Test
    @DisplayName("a null exception message does not blow up the retry bookkeeping")
    void nullError_isSafe() {
        IntegrationEvent e = event(10);

        e.markAttemptFailed(null);

        assertThat(e.getStatus()).isEqualTo(IntegrationEventStatus.PENDING);
        assertThat(e.getAttemptCount()).isEqualTo(1);
    }
}

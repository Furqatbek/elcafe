package com.elcafe.modules.settings.service;

import com.elcafe.modules.settings.repository.PrintJobRepository;
import com.elcafe.modules.settings.websocket.PrintAgentWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The three ways a print job used to stop moving and never move again.
 *
 * <p>All three were dead ends rather than slow paths. A job that failed once went to RETRYING, and the
 * only readers of that status and of its backoff were an unused helper — so it was never sent again,
 * and could not even reach the dead-letter queue, because getting there needs {@code maxRetries}
 * failures and a job nobody re-sends cannot fail twice. The nightly cleanup deletes COMPLETED, FAILED
 * and CANCELLED, so none of the stranded states were ever swept either.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrintJobSweepTest {

    @Mock private PrintJobRepository printJobRepository;
    @Mock private PrintAgentWebSocketHandler printAgentHandler;

    private PrintJobService service;

    @BeforeEach
    void setUp() {
        service = new PrintJobService(printJobRepository, printAgentHandler);
        ReflectionTestUtils.setField(service, "unprintedExpireAfterHours", 24L);
    }

    @Test
    @DisplayName("a job whose backoff has elapsed is put back in the queue")
    void retryingJobs_arePromoted() {
        when(printJobRepository.promoteJobsReadyForRetry(any())).thenReturn(2);

        service.promoteJobsReadyForRetry();

        verify(printJobRepository).promoteJobsReadyForRetry(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("re-queuing tells the agent, rather than waiting for its next reconnect")
    void promotion_notifiesAgents() {
        when(printJobRepository.promoteJobsReadyForRetry(any())).thenReturn(1);

        service.promoteJobsReadyForRetry();

        // A healthy agent may not reconnect for hours. Without this the retry is real but invisible,
        // which is the same ticket not printing for a different reason.
        verify(printAgentHandler).notifyAllAgents();
    }

    @Test
    @DisplayName("nothing to promote wakes nobody up")
    void nothingToPromote_staysQuiet() {
        when(printJobRepository.promoteJobsReadyForRetry(any())).thenReturn(0);

        service.promoteJobsReadyForRetry();

        // This runs every 30 seconds. Broadcasting on every tick would be a message per agent per
        // half-minute, for ever, saying nothing.
        verify(printAgentHandler, never()).notifyAllAgents();
    }

    @Test
    @DisplayName("a job sent and never acknowledged, with its retries spent, reaches the dead-letter queue")
    void abandonedJobs_areDeadLettered() {
        when(printJobRepository.deadLetterAbandonedJobs(any(), any(), anyString())).thenReturn(1);

        service.resetStuckJobs();

        // resetStuckJobs deliberately refuses to re-queue these, and nothing else looked at them, so
        // they sat in SENT for ever — invisible to the queue count and to the operator's DLQ screen.
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(printJobRepository).deadLetterAbandonedJobs(
                any(LocalDateTime.class), any(LocalDateTime.class), reason.capture());
        assertThat(reason.getValue()).contains("retries");
    }

    @Test
    @DisplayName("the stuck sweep still re-queues what it always did")
    void resetStuckJobs_stillRuns() {
        service.resetStuckJobs();

        verify(printJobRepository).resetStuckJobs(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("a ticket nobody printed within the window is retired, not left queued for ever")
    void staleUnprintedJobs_areExpired() {
        when(printJobRepository.expireUnprintedJobs(any(), anyString())).thenReturn(40);

        service.expireUnprintedJobs();

        // The nightly cleanup only ever deleted COMPLETED, FAILED and CANCELLED, so a venue configured
        // for agent printing with no agent installed accrued one PENDING row per order with nothing to
        // remove them.
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(printJobRepository).expireUnprintedJobs(cutoff.capture(), reason.capture());
        assertThat(cutoff.getValue()).isBefore(LocalDateTime.now().minusHours(23));
        assertThat(reason.getValue()).contains("24h");
    }

    @Test
    @DisplayName("retiring stale tickets is its own sweep, not an overnight one")
    void expiry_isNotTiedToTheNightlyCleanup() {
        service.cleanupOldJobs();

        // These rows are counted as waiting tickets by the card, the banner and the alert. On the 3 AM
        // cleanup a venue would spend most of a day being told about a queue that is no longer real.
        verify(printJobRepository, never()).expireUnprintedJobs(any(), anyString());
    }

    @Test
    @DisplayName("the nightly cleanup still deletes what it always did")
    void cleanup_stillDeletesFinishedJobs() {
        service.cleanupOldJobs();

        verify(printJobRepository).deleteOldJobs(any(LocalDateTime.class));
    }
}

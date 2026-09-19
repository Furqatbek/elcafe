package com.elcafe.modules.partner.outbox;

import com.elcafe.modules.partner.entity.IntegrationEvent;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.enums.IntegrationEventStatus;
import com.elcafe.modules.partner.repository.IntegrationEventRepository;
import com.elcafe.modules.partner.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Delivers queued outbound messages, retries the ones that fail, and gives up on the hopeless.
 *
 * <p>Two properties are worth stating because they are easy to lose in a refactor.
 *
 * <p><b>Per-subject ordering.</b> Events that are not coalesced — order transitions — must reach a
 * partner in the order they happened, or their tracking UI walks backwards. So a failure does not just
 * retry that event: it blocks every later event for the same subject in this pass. Without that, an
 * ACCEPTED that failed would be retried while the READY behind it sailed through, and the partner would
 * see READY, then ACCEPTED.
 *
 * <p><b>One transaction per event.</b> A batch is not atomic. One partner being down must not roll back
 * the successful deliveries beside it, and a poison message must not take the batch with it.
 *
 * <p>{@code @SchedulerLock} because delivery is at-least-once and this runs on every node: two nodes
 * dispatching the same batch would double-send everything.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationEventWorker {

    private final IntegrationEventRepository integrationEventRepository;
    private final PartnerRepository partnerRepository;
    private final List<PartnerEventDispatcher> dispatchers;

    /** Off switch. Delivery stops; publishing continues, so nothing is lost while it is off. */
    @Value("${app.partner.outbox.enabled:true}")
    private boolean enabled;

    @Value("${app.partner.outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.partner.outbox.poll-ms:5000}")
    // lockAtLeastFor is 0 deliberately: the lock is here for mutual exclusion while a batch is in
    // flight (lockAtMostFor), not to space runs out — fixedDelay already does that. Holding it after a
    // fast batch would also make the worker un-runnable on demand, which a test and an operator both
    // legitimately want to do.
    @SchedulerLock(name = "partner-integration-outbox", lockAtLeastFor = "PT0S", lockAtMostFor = "PT5M")
    public void dispatchDueEvents() {
        if (!enabled || dispatchers.isEmpty()) {
            return;
        }

        List<IntegrationEvent> due = integrationEventRepository.findDue(
                IntegrationEventStatus.PENDING, OffsetDateTime.now(), PageRequest.of(0, batchSize));
        if (due.isEmpty()) {
            return;
        }

        // Subjects whose earlier event failed in this pass. Anything later for them waits, so a
        // partner never sees a newer state before the older one it supersedes.
        Set<String> blocked = new HashSet<>();
        Map<Long, Optional<Partner>> partnerCache = new HashMap<>();
        int sent = 0;
        int failed = 0;

        for (IntegrationEvent event : due) {
            String subject = event.getPartnerId() + ":" + event.getSubjectKey();
            if (blocked.contains(subject)) {
                continue;
            }

            Optional<Partner> partner = partnerCache.computeIfAbsent(
                    event.getPartnerId(), partnerRepository::findById);
            if (partner.isEmpty() || !Boolean.TRUE.equals(partner.get().getActive())) {
                // A deactivated partner is not a delivery failure to retry — it is a decision. Retiring
                // the event keeps the queue honest instead of accumulating doomed retries.
                supersede(event, "Partner is inactive or gone");
                continue;
            }

            if (deliver(partner.get(), event)) {
                sent++;
            } else {
                failed++;
                blocked.add(subject);
            }
        }

        if (sent > 0 || failed > 0) {
            log.info("Integration outbox: {} delivered, {} failed", sent, failed);
        }
    }

    /** @return true when delivered; false when it failed and this subject should be held back. */
    private boolean deliver(Partner partner, IntegrationEvent event) {
        PartnerEventDispatcher dispatcher = dispatchers.stream()
                .filter(candidate -> candidate.supports(partner))
                .findFirst()
                .orElse(null);

        if (dispatcher == null) {
            // The adapter was removed after the event was queued. Nothing can deliver it.
            supersede(event, "No dispatcher for partner " + partner.getSlug());
            return false;
        }

        try {
            dispatcher.dispatch(partner, event);
            markSent(event);
            return true;
        } catch (Exception e) {
            recordFailure(event, e);
            return false;
        }
    }

    // No @Transactional on these three: they are called from dispatchDueEvents on this same bean, so
    // the proxy would be bypassed and the annotation would be a comforting lie. Each is a single
    // repository save, which SimpleJpaRepository already wraps in its own transaction — which is also
    // exactly the granularity wanted here, one transaction per event.
    private void markSent(IntegrationEvent event) {
        event.markSent();
        integrationEventRepository.save(event);
    }

    private void recordFailure(IntegrationEvent event, Exception e) {
        event.markAttemptFailed(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        integrationEventRepository.save(event);

        if (event.getStatus() == IntegrationEventStatus.DEAD_LETTER) {
            // Loud on purpose: a dead letter means a partner has stopped receiving something we
            // promised to send, and nobody finds that out from a debug line.
            log.error("Integration event {} dead-lettered after {} attempts ({} for {}): {}",
                    event.getId(), event.getAttemptCount(), event.getEventType(),
                    event.getSubjectKey(), event.getLastError());
        } else {
            log.warn("Integration event {} failed (attempt {}/{}), retrying at {}: {}",
                    event.getId(), event.getAttemptCount(), event.getMaxAttempts(),
                    event.getNextAttemptAt(), event.getLastError());
        }
    }

    private void supersede(IntegrationEvent event, String reason) {
        log.warn("Retiring integration event {} ({}): {}", event.getId(), event.getEventType(), reason);
        event.markSuperseded();
        event.setLastError(reason);
        integrationEventRepository.save(event);
    }

    /**
     * Delivered and superseded events are an audit trail with a short useful life; dead letters are
     * deliberately never swept, because they are the record of something we failed to say.
     */
    @Scheduled(cron = "${app.partner.outbox.cleanup-cron:0 30 3 * * *}")
    @SchedulerLock(name = "partner-integration-outbox-cleanup", lockAtLeastFor = "PT30S")
    @Transactional
    public void cleanupSettledEvents() {
        int deleted = integrationEventRepository.deleteSettledBefore(OffsetDateTime.now().minusDays(14));
        if (deleted > 0) {
            log.info("Integration outbox: cleaned up {} settled events", deleted);
        }
    }
}

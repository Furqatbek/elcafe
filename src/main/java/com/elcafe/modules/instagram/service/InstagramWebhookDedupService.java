package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.entity.InstagramProcessedEvent;
import com.elcafe.modules.instagram.repository.InstagramProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Records which webhook events have already been handled so Meta's at-least-once re-deliveries are
 * no-ops. See {@link InstagramProcessedEvent}.
 *
 * <p><b>Not {@code @Transactional} at the class level, deliberately.</b> {@link #firstDelivery} runs on
 * the webhook's {@code @Async} thread, where no transaction is open. Its {@code save} therefore commits
 * in its own (Spring Data) transaction, and the {@link DataIntegrityViolationException} from a racing
 * duplicate surfaces on that save rather than on some outer commit — which is what lets the catch below
 * turn it into a clean {@code false}. Making this bean {@code @Transactional} would instead mark the
 * ambient transaction rollback-only on the constraint hit, converting a benign duplicate into an
 * {@code UnexpectedRollbackException} at commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramWebhookDedupService {

    private final InstagramProcessedEventRepository repository;

    /** How long a processed-event row is kept. Comfortably longer than Meta's re-delivery window. */
    @Value("${instagram.webhook.dedup-retention-days:7}")
    private long retentionDays;

    /**
     * Check-and-record: {@code true} the first time we see {@code (restaurantId, eventId)} — the caller
     * should process the event — or {@code false} if it is a re-delivery to skip.
     *
     * <p>An event with no usable id ({@code null}/blank) cannot be deduplicated, so it is treated as a
     * first delivery and processed: Meta always stamps real messages and comments with an id, and
     * failing open here keeps a legitimate event from being dropped over a missing key.
     *
     * <p>The caller runs with {@link com.elcafe.common.tenant.TenantContext} already bound to
     * {@code restaurantId}, so the insert satisfies the {@code TenantInsertGuard} and the fast-path
     * check runs under the restaurantFilter.
     */
    public boolean firstDelivery(Long restaurantId, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }
        if (repository.existsByRestaurantIdAndEventId(restaurantId, eventId)) {
            log.debug("Instagram webhook event {} already processed for restaurant {} — skipping",
                    eventId, restaurantId);
            return false;
        }
        try {
            repository.save(InstagramProcessedEvent.builder()
                    .restaurantId(restaurantId)
                    .eventId(eventId)
                    .processedAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build());
            return true;
        } catch (DataIntegrityViolationException race) {
            // A concurrent re-delivery inserted the same (restaurant_id, event_id) between our check and
            // this save. The unique constraint is the arbiter; whoever loses the race treats it as a dup.
            log.debug("Instagram webhook event {} raced a concurrent delivery for restaurant {} — skipping",
                    eventId, restaurantId);
            return false;
        }
    }

    /**
     * Keep the dedup table bounded. Meta stops re-delivering an event within hours, so anything older
     * than the retention window is dead weight. Cross-tenant by design — runs with no tenant bound, so
     * the restaurantFilter is inactive and the sweep sees every tenant's rows. Guarded by ShedLock so
     * only one node in a multi-instance deployment runs it.
     */
    @Scheduled(cron = "${instagram.webhook.dedup-cleanup-cron:0 30 3 * * *}")
    @SchedulerLock(name = "instagram-dedup-cleanup", lockAtLeastFor = "PT30S")
    @Transactional
    public void purgeOldEvents() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);
        int deleted = repository.deleteByProcessedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} processed Instagram webhook events older than {} days", deleted, retentionDays);
        }
    }
}

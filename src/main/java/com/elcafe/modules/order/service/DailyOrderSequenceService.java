package com.elcafe.modules.order.service;

import com.elcafe.modules.order.entity.DailyOrderSequence;
import com.elcafe.modules.order.repository.DailyOrderSequenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyOrderSequenceService {

    private final DailyOrderSequenceRepository dailyOrderSequenceRepository;

    @Transactional
    public String generateNextOrderNumber() {
        LocalDate today = LocalDate.now();
        int nextSequence = claimNextSequence(today);

        // Format: ORD-YYYYMMDD-XXXX (e.g., ORD-20251220-0001)
        String orderNumber = String.format("ORD-%s-%04d",
                today.toString().replace("-", ""),
                nextSequence);

        log.debug("Generated order number: {} for date: {}", orderNumber, today);
        return orderNumber;
    }

    /**
     * Take the next number for today, and never touch the row's {@code date} while doing it.
     *
     * <p>That restraint is the whole point of this method. The obvious implementation — load the row,
     * increment the field, save the entity — writes every column back, {@code date} included. Write a
     * date you have just read, and any single off-by-one on the way in or out stops being a cosmetic
     * oddity and becomes permanent: the stored date moves a day every time an order is numbered, and
     * on the pass where it has moved far enough that the lookup misses, a second row is created, the
     * counter restarts at 1, and we hand out an order number that already exists. The unique index on
     * {@code orders.order_number} then refuses the order.
     *
     * <p>That is not hypothetical — it reproduced under H2 once the JVM's default zone was changed
     * after the connection pool had already captured a different one, which is what
     * {@code RestaurantDeliveryApplication} used to do from a {@code @PostConstruct}. The zone is now
     * set once before anything can capture it, so the read is faithful again; this method is the
     * second lock on the same door, because a value we never rewrite cannot drift however the
     * conversion behaves.
     *
     * <p>Concurrency comes from the single UPDATE, which holds the row lock until commit, so a second
     * request increments the committed value rather than a stale copy. The read after it is a scalar
     * projection, so it returns this transaction's own number and not something from the session
     * cache. Two requests racing to create the very first row of the day are still settled by the
     * unique index on {@code date} — one of them loses, as it did before.
     */
    private int claimNextSequence(LocalDate today) {
        if (dailyOrderSequenceRepository.incrementSequence(today) == 0) {
            log.info("Creating new daily order sequence for date: {}", today);
            dailyOrderSequenceRepository.save(DailyOrderSequence.builder()
                    .date(today)
                    .currentSequence(1)
                    .build());
            return 1;
        }
        return dailyOrderSequenceRepository.findSequenceByDate(today)
                .orElseThrow(() -> new IllegalStateException(
                        "Daily order sequence for " + today + " disappeared after being incremented"));
    }

    @Transactional
    public void cleanupOldSequences() {
        // Keep last 7 days of sequences for reference
        LocalDate cutoffDate = LocalDate.now().minusDays(7);
        dailyOrderSequenceRepository.deleteByDateBefore(cutoffDate);
        log.info("Cleaned up order sequences before date: {}", cutoffDate);
    }
}

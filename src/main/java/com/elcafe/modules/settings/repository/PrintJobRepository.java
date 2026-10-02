package com.elcafe.modules.settings.repository;

import com.elcafe.modules.settings.entity.PrintJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PrintJobRepository extends JpaRepository<PrintJob, Long> {

    /**
     * Find pending print jobs for a restaurant, ordered by creation time
     */
    @Query("SELECT pj FROM PrintJob pj " +
           "JOIN FETCH pj.printer " +
           "WHERE pj.restaurant.id = :restaurantId " +
           "AND pj.status = 'PENDING' " +
           "ORDER BY pj.createdAt ASC")
    List<PrintJob> findPendingJobsByRestaurant(@Param("restaurantId") Long restaurantId);

    /**
     * The same queue, minus the tickets that are no longer worth printing.
     *
     * <p>What an agent gets handed when it connects. The unbounded version is what made a reconnect
     * dangerous: a venue whose kitchen machine was off all day comes back and a live printer spools
     * every ticket since the outage started, long after the food went out. The background sweep retires
     * these too, but on a schedule — this is the guarantee that does not depend on when it last ran.
     */
    @Query("SELECT pj FROM PrintJob pj " +
           "JOIN FETCH pj.printer " +
           "WHERE pj.restaurant.id = :restaurantId " +
           "AND pj.status = 'PENDING' " +
           "AND pj.createdAt >= :notBefore " +
           "ORDER BY pj.createdAt ASC")
    List<PrintJob> findPendingJobsByRestaurantSince(@Param("restaurantId") Long restaurantId,
                                                    @Param("notBefore") LocalDateTime notBefore);

    /**
     * Find pending print jobs for a specific printer
     */
    @Query("SELECT pj FROM PrintJob pj " +
           "JOIN FETCH pj.printer " +
           "WHERE pj.printer.id = :printerId " +
           "AND pj.status = 'PENDING' " +
           "ORDER BY pj.createdAt ASC")
    List<PrintJob> findPendingJobsByPrinter(@Param("printerId") Long printerId);

    /**
     * Find all pending jobs that can be retried
     */
    @Query("SELECT pj FROM PrintJob pj " +
           "JOIN FETCH pj.printer " +
           "WHERE pj.status IN ('PENDING', 'SENT') " +
           "AND pj.retryCount < pj.maxRetries " +
           "ORDER BY pj.createdAt ASC")
    List<PrintJob> findRetryableJobs();

    /**
     * Find jobs by status
     */
    List<PrintJob> findByRestaurant_IdAndStatus(Long restaurantId, PrintJob.PrintJobStatus status);

    /**
     * Find recent jobs for a restaurant
     */
    @Query("SELECT pj FROM PrintJob pj " +
           "WHERE pj.restaurant.id = :restaurantId " +
           "AND pj.createdAt > :since " +
           "ORDER BY pj.createdAt DESC")
    List<PrintJob> findRecentJobs(@Param("restaurantId") Long restaurantId, @Param("since") LocalDateTime since);

    /**
     * Count pending jobs for a restaurant
     */
    long countByRestaurant_IdAndStatus(Long restaurantId, PrintJob.PrintJobStatus status);

    /**
     * Tickets this venue has not managed to print yet.
     *
     * <p>Deliberately wider than PENDING. A job SENT to an agent that then died, or PRINTING when the
     * paper ran out, or RETRYING after a refusal, is a ticket nobody has in their hand — and the
     * question a venue is asking when they look at this is "how many orders are not on the rail",
     * not "how many are in a particular internal state".
     */
    @Query("SELECT COUNT(pj) FROM PrintJob pj WHERE pj.restaurant.id = :restaurantId "
            + "AND pj.status IN ('PENDING', 'SENT', 'PRINTING', 'RETRYING')")
    long countUnprinted(@Param("restaurantId") Long restaurantId);

    /** When the oldest of those was created, which is how long the kitchen has been behind. */
    @Query("SELECT MIN(pj.createdAt) FROM PrintJob pj WHERE pj.restaurant.id = :restaurantId "
            + "AND pj.status IN ('PENDING', 'SENT', 'PRINTING', 'RETRYING')")
    LocalDateTime oldestUnprintedAt(@Param("restaurantId") Long restaurantId);

    /**
     * Delete old completed/failed jobs (cleanup)
     */
    @Modifying
    @Query("DELETE FROM PrintJob pj " +
           "WHERE pj.status IN ('COMPLETED', 'FAILED', 'CANCELLED') " +
           "AND pj.createdAt < :before")
    int deleteOldJobs(@Param("before") LocalDateTime before);

    /**
     * Reset stuck jobs (sent but not completed after timeout)
     */
    @Modifying
    @Query("UPDATE PrintJob pj SET pj.status = 'PENDING', pj.retryCount = pj.retryCount + 1 " +
           "WHERE pj.status = 'SENT' " +
           "AND pj.updatedAt < :timeout " +
           "AND pj.retryCount < pj.maxRetries")
    int resetStuckJobs(@Param("timeout") LocalDateTime timeout);

    /**
     * Put a job whose backoff has elapsed back in the queue.
     *
     * <p>Without this nothing ever leaves RETRYING. {@code markJobFailed} computes a backoff and sets
     * the status, and the only reader of either was an unused helper — so a ticket that failed once was
     * never sent again, never retried, and never dead-lettered, because reaching the dead-letter queue
     * requires failing {@code maxRetries} times and a job nobody re-sends cannot fail twice.
     */
    @Modifying
    @Query("UPDATE PrintJob pj SET pj.status = 'PENDING' "
            + "WHERE pj.status = 'RETRYING' "
            + "AND (pj.nextRetryAt IS NULL OR pj.nextRetryAt <= :now)")
    int promoteJobsReadyForRetry(@Param("now") LocalDateTime now);

    /**
     * A job sent to an agent that never answered, with its retries already spent.
     *
     * <p>{@code resetStuckJobs} deliberately will not re-queue these, and nothing else looked at them,
     * so they sat in SENT for ever. They belong in the dead-letter queue: we tried as often as we said
     * we would, and a person should decide what happens to the ticket.
     */
    @Modifying
    @Query("UPDATE PrintJob pj SET pj.status = 'DEAD_LETTER', pj.movedToDlqAt = :now, "
            + "pj.dlqReason = :reason "
            + "WHERE pj.status = 'SENT' AND pj.updatedAt < :timeout "
            + "AND pj.retryCount >= pj.maxRetries")
    int deadLetterAbandonedJobs(@Param("timeout") LocalDateTime timeout,
                                @Param("now") LocalDateTime now,
                                @Param("reason") String reason);

    /**
     * Retire a ticket nobody is ever going to print.
     *
     * <p>Two problems, one sweep. A venue configured for agent printing with no agent installed queues
     * one of these per order for ever, and nothing cleaned them — the nightly job only removes
     * COMPLETED, FAILED and CANCELLED. And when an agent finally does connect, every pending job for
     * that venue is pushed at once, so a printer would spool days of dead tickets.
     *
     * <p>CANCELLED rather than the dead-letter queue on purpose: the queue is for the handful of things
     * we tried and failed at, and it is kept for ever as that record. These are unbounded by order
     * volume rather than by failure, so they go somewhere the existing cleanup will sweep them a week
     * later — long enough to be noticed, and nothing silently vanishes, because a venue in this state
     * has a banner across its screens the whole time.
     */
    @Modifying
    @Query("UPDATE PrintJob pj SET pj.status = 'CANCELLED', pj.errorMessage = :reason "
            + "WHERE pj.status IN ('PENDING', 'RETRYING') AND pj.createdAt < :cutoff")
    int expireUnprintedJobs(@Param("cutoff") LocalDateTime cutoff, @Param("reason") String reason);

    /**
     * Find jobs ready for retry (in RETRYING status and past their backoff time)
     */
    @Query("SELECT pj FROM PrintJob pj " +
           "JOIN FETCH pj.printer " +
           "WHERE pj.restaurant.id = :restaurantId " +
           "AND pj.status = 'RETRYING' " +
           "AND (pj.nextRetryAt IS NULL OR pj.nextRetryAt <= :now) " +
           "ORDER BY pj.priority ASC, pj.createdAt ASC")
    List<PrintJob> findJobsReadyForRetry(@Param("restaurantId") Long restaurantId, @Param("now") LocalDateTime now);

    /**
     * Find pending jobs with priority ordering
     */
    @Query("SELECT pj FROM PrintJob pj " +
           "JOIN FETCH pj.printer " +
           "WHERE pj.restaurant.id = :restaurantId " +
           "AND pj.status = 'PENDING' " +
           "ORDER BY pj.priority ASC, pj.createdAt ASC")
    List<PrintJob> findPendingJobsByRestaurantWithPriority(@Param("restaurantId") Long restaurantId);

    /**
     * Count jobs in dead-letter queue for a restaurant
     */
    @Query("SELECT COUNT(pj) FROM PrintJob pj " +
           "WHERE pj.restaurant.id = :restaurantId AND pj.status = 'DEAD_LETTER'")
    long countDeadLetterJobs(@Param("restaurantId") Long restaurantId);

    /**
     * Find all jobs in dead-letter queue
     */
    @Query("SELECT pj FROM PrintJob pj " +
           "JOIN FETCH pj.printer " +
           "WHERE pj.status = 'DEAD_LETTER' " +
           "ORDER BY pj.movedToDlqAt DESC")
    List<PrintJob> findAllDeadLetterJobs();

    /**
     * Move jobs to dead-letter queue that have exceeded max retries
     */
    @Modifying
    @Query("UPDATE PrintJob pj SET " +
           "pj.status = 'DEAD_LETTER', " +
           "pj.movedToDlqAt = :now, " +
           "pj.dlqReason = 'Max retries exceeded' " +
           "WHERE pj.status = 'RETRYING' " +
           "AND pj.retryCount >= pj.maxRetries")
    int moveExhaustedJobsToDlq(@Param("now") LocalDateTime now);
}

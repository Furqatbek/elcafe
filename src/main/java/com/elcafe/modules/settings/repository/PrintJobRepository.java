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

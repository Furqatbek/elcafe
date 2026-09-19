package com.elcafe.modules.partner.repository;

import com.elcafe.modules.partner.entity.IntegrationEvent;
import com.elcafe.modules.partner.enums.IntegrationEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface IntegrationEventRepository extends JpaRepository<IntegrationEvent, Long> {

    /**
     * The worker's claim query: everything due now, oldest first.
     *
     * <p>Ordered by id rather than {@code nextAttemptAt} on purpose. Two events for the same subject
     * must be delivered in the order they happened, and ids are the only field that reflects that —
     * a retried event's {@code nextAttemptAt} has been pushed into the future, so ordering by it would
     * let a newer sibling overtake it.
     */
    @Query("SELECT e FROM IntegrationEvent e WHERE e.status = :status AND e.nextAttemptAt <= :now "
            + "ORDER BY e.id ASC")
    List<IntegrationEvent> findDue(@Param("status") IntegrationEventStatus status,
                                   @Param("now") OffsetDateTime now,
                                   Pageable pageable);

    /**
     * Supersedes older pending events for the same subject, in one statement.
     *
     * <p>A bulk update rather than load-and-save: an item flapping across its stock threshold can leave
     * dozens of pending rows, and this runs inside the caller's transaction on the hot path.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE IntegrationEvent e SET e.status = com.elcafe.modules.partner.enums.IntegrationEventStatus.SUPERSEDED, "
            + "e.updatedAt = :now "
            + "WHERE e.partnerId = :partnerId AND e.subjectKey = :subjectKey "
            + "AND e.eventType = :eventType "
            + "AND e.status = com.elcafe.modules.partner.enums.IntegrationEventStatus.PENDING")
    int supersedePending(@Param("partnerId") Long partnerId,
                         @Param("subjectKey") String subjectKey,
                         @Param("eventType") com.elcafe.modules.partner.enums.IntegrationEventType eventType,
                         @Param("now") OffsetDateTime now);

    List<IntegrationEvent> findByPartnerIdAndStatusOrderByCreatedAtDesc(
            Long partnerId, IntegrationEventStatus status, Pageable pageable);

    long countByPartnerIdAndStatus(Long partnerId, IntegrationEventStatus status);

    long countByStatus(IntegrationEventStatus status);

    /** Housekeeping: delivered events are an audit trail with a short useful life. */
    @Modifying
    @Query("DELETE FROM IntegrationEvent e WHERE e.status IN "
            + "(com.elcafe.modules.partner.enums.IntegrationEventStatus.SENT, "
            + " com.elcafe.modules.partner.enums.IntegrationEventStatus.SUPERSEDED) "
            + "AND e.createdAt < :cutoff")
    int deleteSettledBefore(@Param("cutoff") OffsetDateTime cutoff);
}

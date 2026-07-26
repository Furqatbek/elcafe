package com.elcafe.modules.instagram.repository;

import com.elcafe.modules.instagram.entity.InstagramProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface InstagramProcessedEventRepository extends JpaRepository<InstagramProcessedEvent, Long> {

    /** Fast-path duplicate check before attempting the insert. Served by the uq_ig_processed_event index. */
    boolean existsByRestaurantIdAndEventId(Long restaurantId, String eventId);

    /**
     * Retention sweep: drop dedup rows older than the cutoff. Meta stops re-delivering an event within
     * hours, so a row past the retention window can never match a live re-delivery again. Runs on the
     * scheduler thread with no tenant bound, so the restaurantFilter is inactive and this spans all
     * tenants — exactly what a maintenance sweep wants.
     */
    @Modifying
    @Query("DELETE FROM InstagramProcessedEvent e WHERE e.processedAt < :cutoff")
    int deleteByProcessedAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}

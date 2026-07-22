package com.elcafe.modules.instagram.repository;

import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InstagramSubscriberRepository extends JpaRepository<InstagramSubscriber, Long> {

    Optional<InstagramSubscriber> findByIgsid(String igsid);

    Page<InstagramSubscriber> findByIsActiveTrue(Pageable pageable);

    @Query("SELECT COUNT(s) FROM InstagramSubscriber s WHERE s.isActive = true AND s.isBlocked = false " +
           "AND (s.conversationState IS NULL OR s.conversationState = 'REGISTERED')")
    long countRegistered();

    @Query("SELECT s FROM InstagramSubscriber s WHERE " +
           "LOWER(s.username) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(s.displayName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "s.phone LIKE CONCAT('%', :q, '%')")
    Page<InstagramSubscriber> search(@Param("q") String query, Pageable pageable);

    /** All active, non-blocked subscribers (used for broadcast). */
    @Query("SELECT s FROM InstagramSubscriber s WHERE s.isActive = true AND s.isBlocked = false")
    java.util.List<InstagramSubscriber> findAllActiveNotBlocked();

    /** Active, non-blocked, fully registered subscribers (used for targeted broadcast). */
    @Query("SELECT s FROM InstagramSubscriber s WHERE s.isActive = true AND s.isBlocked = false " +
           "AND s.conversationState = 'REGISTERED'")
    java.util.List<InstagramSubscriber> findAllRegistered();
}

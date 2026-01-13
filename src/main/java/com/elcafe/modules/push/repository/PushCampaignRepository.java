package com.elcafe.modules.push.repository;

import com.elcafe.modules.push.entity.PushCampaign;
import com.elcafe.modules.push.enums.PushCampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PushCampaignRepository extends JpaRepository<PushCampaign, Long> {

    /**
     * Find all campaigns ordered by creation date
     */
    Page<PushCampaign> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Find campaigns by status
     */
    Page<PushCampaign> findByStatusOrderByCreatedAtDesc(PushCampaignStatus status, Pageable pageable);

    /**
     * Find scheduled campaigns ready to send
     */
    @Query("SELECT c FROM PushCampaign c WHERE c.status = 'SCHEDULED' AND c.scheduledAt <= :now")
    List<PushCampaign> findScheduledCampaignsReadyToSend(@Param("now") LocalDateTime now);

    /**
     * Count by status
     */
    long countByStatus(PushCampaignStatus status);

    /**
     * Search campaigns by name
     */
    @Query("SELECT c FROM PushCampaign c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY c.createdAt DESC")
    Page<PushCampaign> searchByName(@Param("query") String query, Pageable pageable);

    /**
     * Get total notifications sent
     */
    @Query("SELECT COALESCE(SUM(c.sentCount), 0) FROM PushCampaign c")
    long getTotalSentCount();

    /**
     * Get total clicks
     */
    @Query("SELECT COALESCE(SUM(c.clickedCount), 0) FROM PushCampaign c")
    long getTotalClickedCount();

    /**
     * Find campaigns completed in date range
     */
    @Query("SELECT c FROM PushCampaign c WHERE c.status = 'COMPLETED' AND c.completedAt BETWEEN :start AND :end")
    List<PushCampaign> findCompletedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Find campaigns with best click rate
     */
    @Query("SELECT c FROM PushCampaign c WHERE c.status = 'COMPLETED' AND c.sentCount > 0 " +
           "ORDER BY (CAST(c.clickedCount AS double) / c.sentCount) DESC")
    List<PushCampaign> findTopPerformingCampaigns(Pageable pageable);
}

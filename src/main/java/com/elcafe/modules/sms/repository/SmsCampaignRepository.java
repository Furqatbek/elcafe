package com.elcafe.modules.sms.repository;

import com.elcafe.modules.sms.entity.SmsCampaign;
import com.elcafe.modules.sms.enums.CampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SmsCampaignRepository extends JpaRepository<SmsCampaign, Long> {

    Page<SmsCampaign> findByStatus(CampaignStatus status, Pageable pageable);

    List<SmsCampaign> findByStatus(CampaignStatus status);

    @Query("SELECT c FROM SmsCampaign c WHERE c.status = :status AND c.scheduledAt <= :now")
    List<SmsCampaign> findScheduledCampaignsReadyToSend(
            @Param("status") CampaignStatus status,
            @Param("now") LocalDateTime now);

    @Query("SELECT c FROM SmsCampaign c WHERE c.status IN :statuses ORDER BY c.createdAt DESC")
    Page<SmsCampaign> findByStatusIn(@Param("statuses") List<CampaignStatus> statuses, Pageable pageable);

    @Query("SELECT c FROM SmsCampaign c WHERE c.createdAt >= :since ORDER BY c.createdAt DESC")
    List<SmsCampaign> findRecentCampaigns(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(c) FROM SmsCampaign c WHERE c.status = :status")
    long countByStatus(@Param("status") CampaignStatus status);

    @Query("SELECT SUM(c.sentCount) FROM SmsCampaign c WHERE c.completedAt >= :since")
    Long getTotalSentSince(@Param("since") LocalDateTime since);

    @Query("SELECT SUM(c.deliveredCount) FROM SmsCampaign c WHERE c.completedAt >= :since")
    Long getTotalDeliveredSince(@Param("since") LocalDateTime since);

    @Query("SELECT SUM(c.totalCost) FROM SmsCampaign c WHERE c.completedAt >= :since")
    BigDecimal getTotalCostSince(@Param("since") LocalDateTime since);

    @Query("SELECT c FROM SmsCampaign c WHERE c.scheduledAt >= :from AND c.scheduledAt <= :to ORDER BY c.scheduledAt")
    List<SmsCampaign> findScheduledBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT c FROM SmsCampaign c LEFT JOIN FETCH c.template WHERE c.id = :id")
    SmsCampaign findByIdWithTemplate(@Param("id") Long id);

    /**
     * Find scheduled campaigns that are due to be sent.
     */
    List<SmsCampaign> findByStatusAndScheduledAtBefore(CampaignStatus status, LocalDateTime dateTime);

    /**
     * Count campaigns by status and created before a certain date.
     */
    long countByStatusAndCreatedAtBefore(CampaignStatus status, LocalDateTime dateTime);
}

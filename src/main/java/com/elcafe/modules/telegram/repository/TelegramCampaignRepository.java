package com.elcafe.modules.telegram.repository;

import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.telegram.entity.TelegramCampaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TelegramCampaignRepository extends JpaRepository<TelegramCampaign, Long> {

    Page<TelegramCampaign> findByStatus(CampaignStatus status, Pageable pageable);

    List<TelegramCampaign> findByStatusAndScheduledAtBefore(CampaignStatus status, LocalDateTime before);

    @Query("SELECT c FROM TelegramCampaign c LEFT JOIN FETCH c.template WHERE c.id = :id")
    TelegramCampaign findByIdWithTemplate(@Param("id") Long id);

    @Query("SELECT COUNT(c) FROM TelegramCampaign c WHERE c.status = :status")
    long countByStatus(@Param("status") CampaignStatus status);

    @Query("SELECT c FROM TelegramCampaign c WHERE c.createdAt >= :since ORDER BY c.createdAt DESC")
    List<TelegramCampaign> findRecentCampaigns(@Param("since") LocalDateTime since);
}

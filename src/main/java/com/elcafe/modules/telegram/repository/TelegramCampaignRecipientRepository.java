package com.elcafe.modules.telegram.repository;

import com.elcafe.modules.sms.enums.MessageStatus;
import com.elcafe.modules.telegram.entity.TelegramCampaignRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelegramCampaignRecipientRepository extends JpaRepository<TelegramCampaignRecipient, Long> {

    Page<TelegramCampaignRecipient> findByCampaignId(Long campaignId, Pageable pageable);

    List<TelegramCampaignRecipient> findByCampaignIdAndStatus(Long campaignId, MessageStatus status);

    @Query("SELECT r FROM TelegramCampaignRecipient r WHERE r.campaign.id = :campaignId AND r.status = 'PENDING'")
    List<TelegramCampaignRecipient> findPendingByCampaignId(@Param("campaignId") Long campaignId);

    @Query("SELECT r.status, COUNT(r) FROM TelegramCampaignRecipient r WHERE r.campaign.id = :campaignId GROUP BY r.status")
    List<Object[]> getStatusCountsByCampaign(@Param("campaignId") Long campaignId);

    long countByCampaignId(Long campaignId);
}

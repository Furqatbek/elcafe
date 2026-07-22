package com.elcafe.modules.sms.repository;

import com.elcafe.modules.sms.entity.SmsCampaignRecipient;
import com.elcafe.modules.sms.enums.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SmsCampaignRecipientRepository extends JpaRepository<SmsCampaignRecipient, Long> {

    Page<SmsCampaignRecipient> findByCampaignId(Long campaignId, Pageable pageable);

    List<SmsCampaignRecipient> findByCampaignIdAndStatus(Long campaignId, MessageStatus status);

    @Query("SELECT r FROM SmsCampaignRecipient r WHERE r.campaign.id = :campaignId AND r.status = 'PENDING'")
    List<SmsCampaignRecipient> findPendingByCampaignId(@Param("campaignId") Long campaignId);

    @Query("SELECT COUNT(r) FROM SmsCampaignRecipient r WHERE r.campaign.id = :campaignId AND r.status = :status")
    long countByCampaignIdAndStatus(@Param("campaignId") Long campaignId, @Param("status") MessageStatus status);

    Optional<SmsCampaignRecipient> findByEskizMessageId(Long eskizMessageId);

    @Modifying
    @Query("UPDATE SmsCampaignRecipient r SET r.status = :status WHERE r.campaign.id = :campaignId AND r.status = 'PENDING'")
    int updateStatusForPending(@Param("campaignId") Long campaignId, @Param("status") MessageStatus status);

    @Query("SELECT r.status, COUNT(r) FROM SmsCampaignRecipient r WHERE r.campaign.id = :campaignId GROUP BY r.status")
    List<Object[]> getStatusCountsByCampaign(@Param("campaignId") Long campaignId);

    boolean existsByCampaignIdAndPhone(Long campaignId, String phone);

    @Query("SELECT r FROM SmsCampaignRecipient r WHERE r.campaign.id = :campaignId AND r.status IN ('SENT', 'WAITING') AND r.sentAt IS NOT NULL")
    List<SmsCampaignRecipient> findSentRecipientsForStatusCheck(@Param("campaignId") Long campaignId);
}

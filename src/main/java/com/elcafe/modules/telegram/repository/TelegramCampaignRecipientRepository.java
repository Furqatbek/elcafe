package com.elcafe.modules.telegram.repository;

import com.elcafe.modules.sms.enums.MessageStatus;
import com.elcafe.modules.telegram.entity.TelegramCampaignRecipient;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
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

    /**
     * Same as {@link #findByCampaignIdAndStatus} but eagerly fetches each recipient's subscriber.
     * The campaign send loop runs on an {@code @Async} thread with no bound persistence session, so a
     * lazy {@code recipient.subscriber} access there would depend on {@code enable_lazy_load_no_trans}
     * (a connection-per-access crutch) and would throw once that is turned off. Fetch it up front.
     */
    @Query("SELECT r FROM TelegramCampaignRecipient r LEFT JOIN FETCH r.subscriber "
            + "WHERE r.campaign.id = :campaignId AND r.status = :status")
    List<TelegramCampaignRecipient> findByCampaignIdAndStatusWithSubscriber(
            @Param("campaignId") Long campaignId, @Param("status") MessageStatus status);

    @Query("SELECT r FROM TelegramCampaignRecipient r WHERE r.campaign.id = :campaignId AND r.status = 'PENDING'")
    List<TelegramCampaignRecipient> findPendingByCampaignId(@Param("campaignId") Long campaignId);

    @Query("SELECT r.status, COUNT(r) FROM TelegramCampaignRecipient r WHERE r.campaign.id = :campaignId GROUP BY r.status")
    List<Object[]> getStatusCountsByCampaign(@Param("campaignId") Long campaignId);

    long countByCampaignId(Long campaignId);

    /** Erase a subscriber's campaign-recipient rows (called when the subscriber itself is deleted). */
    void deleteBySubscriber(TelegramSubscriber subscriber);
}

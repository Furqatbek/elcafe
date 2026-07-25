package com.elcafe.modules.instagram.repository;

import com.elcafe.modules.instagram.entity.InstagramCampaignRecipient;
import com.elcafe.modules.sms.enums.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstagramCampaignRecipientRepository extends JpaRepository<InstagramCampaignRecipient, Long> {

    /**
     * Recipients of one campaign in a given status. {@code igsid} is a column on the recipient, so the
     * send loop needs no lazy subscriber access — safe to run on the {@code @Async} executor thread.
     */
    List<InstagramCampaignRecipient> findByCampaignIdAndStatus(Long campaignId, MessageStatus status);

    Page<InstagramCampaignRecipient> findByCampaignId(Long campaignId, Pageable pageable);

    @Query("SELECT r.status, COUNT(r) FROM InstagramCampaignRecipient r "
            + "WHERE r.campaign.id = :campaignId GROUP BY r.status")
    List<Object[]> getStatusCountsByCampaign(@Param("campaignId") Long campaignId);
}

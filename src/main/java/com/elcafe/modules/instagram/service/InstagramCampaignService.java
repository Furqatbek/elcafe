package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.instagram.dto.InstagramCampaignRecipientResponse;
import com.elcafe.modules.instagram.dto.InstagramCampaignRequest;
import com.elcafe.modules.instagram.dto.InstagramCampaignResponse;
import com.elcafe.modules.instagram.entity.InstagramCampaign;
import com.elcafe.modules.instagram.entity.InstagramCampaignRecipient;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.enums.InstagramCampaignAudience;
import com.elcafe.modules.instagram.repository.InstagramCampaignRecipientRepository;
import com.elcafe.modules.instagram.repository.InstagramCampaignRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.sms.enums.MessageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Instagram marketing campaigns: persist a campaign and its per-recipient rows, then hand the send to
 * {@link InstagramCampaignExecutor} on an {@code @Async} thread. Replaces the old synchronous
 * {@code InstagramBotService.broadcast}, which looped on the request thread (504 past the proxy timeout)
 * and kept no record (a re-run double-sent).
 *
 * <p>Every read and write is scoped to the caller's own restaurant — a campaign or recipient id from
 * another tenant reads as not-found, and a platform (SUPER_ADMIN) account cannot create one because a
 * per-tenant channel campaign must belong to exactly one restaurant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramCampaignService {

    private final InstagramCampaignRepository campaignRepository;
    private final InstagramCampaignRecipientRepository recipientRepository;
    private final InstagramSubscriberRepository subscriberRepository;
    private final InstagramCampaignExecutor executor;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    /**
     * Instagram's standard messaging window: a user may be DM'd only within this many hours of their
     * last inbound message. The audience is built from it so a campaign never enqueues sends Meta will
     * reject (code 10). Marketing content is not eligible for any message tag that would extend the
     * window, so filtering — not tagging — is the correct fix. Slightly under 24 for clock/queue slack.
     */
    @Value("${instagram.campaign.messaging-window-hours:24}")
    private int messagingWindowHours;

    /** Create a campaign with its PENDING recipient rows and immediately start the async send. */
    @Transactional
    public InstagramCampaignResponse createAndSend(InstagramCampaignRequest request) {
        InstagramCampaign campaign = createInternal(request);
        return startSending(campaign.getId());
    }

    /** Create a campaign in DRAFT with its recipient rows, without sending yet. */
    @Transactional
    public InstagramCampaignResponse createCampaign(InstagramCampaignRequest request) {
        return InstagramCampaignResponse.from(createInternal(request));
    }

    private InstagramCampaign createInternal(InstagramCampaignRequest request) {
        Long restaurantId = requireWritableTenant();
        if (request.getMessageText() == null || request.getMessageText().isBlank()) {
            throw new BadRequestException("A campaign message is required.");
        }
        InstagramCampaignAudience audience = parseAudience(request.getTargetAudience());
        String imageUrl = normalizeImageUrl(request.getImageUrl());

        InstagramCampaign campaign = campaignRepository.save(InstagramCampaign.builder()
                .restaurantId(restaurantId)
                .name(defaultName(request.getName()))
                .messageText(request.getMessageText())
                .imageUrl(imageUrl)
                .targetAudience(audience)
                .status(CampaignStatus.DRAFT)
                .build());

        // Instagram only permits DMing a user within ~24h of their last inbound message. Build the
        // audience from that window (lastInteractionAt) so the campaign never enqueues sends Meta will
        // reject with code 10 — sustained, the thing that gets an app restricted.
        //
        // V174: both finders ALSO require marketingOptIn = true, for both ALL and REGISTERED — so
        // recipientCount can legitimately be lower than "everyone in the window" by exactly the number
        // of subscribers who typed a STOP-family keyword. That is the point, not a bug: a campaign must
        // never re-message someone who opted out, however recently they last interacted.
        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusHours(messagingWindowHours);
        List<InstagramSubscriber> subscribers = audience == InstagramCampaignAudience.REGISTERED
                ? subscriberRepository.findAllRegisteredSince(restaurantId, since)
                : subscriberRepository.findAllActiveNotBlockedSince(restaurantId, since);

        final InstagramCampaign owner = campaign;
        List<InstagramCampaignRecipient> rows = subscribers.stream()
                .map(s -> InstagramCampaignRecipient.builder()
                        .restaurantId(restaurantId)   // the campaign's own tenant, never guessed
                        .campaign(owner)
                        .subscriber(s)
                        .igsid(s.getIgsid())
                        .status(MessageStatus.PENDING)
                        .build())
                .toList();
        recipientRepository.saveAll(rows);

        campaign.setRecipientCount(rows.size());
        campaign = campaignRepository.save(campaign);
        log.info("Created Instagram campaign {} for restaurant {} with {} recipients",
                campaign.getId(), restaurantId, rows.size());
        return campaign;
    }

    /**
     * Move a campaign to SENDING and trigger the async run. Allowed from DRAFT or from a previously
     * halted CANCELLED run (a re-send that only retries the recipients still PENDING).
     */
    @Transactional
    public InstagramCampaignResponse startSending(Long id) {
        InstagramCampaign campaign = findForCallerOrThrow(id);
        if (campaign.getStatus() == CampaignStatus.SENDING) {
            throw new BadRequestException("Campaign is already sending.");
        }
        if (campaign.getStatus() == CampaignStatus.COMPLETED) {
            throw new BadRequestException("Campaign has already completed.");
        }
        if (campaign.getRecipientCount() == 0) {
            throw new BadRequestException("Campaign has no recipients.");
        }
        campaign.setStatus(CampaignStatus.SENDING);
        campaign.setStartedAt(OffsetDateTime.now(ZoneOffset.UTC));
        campaign.setCompletedAt(null);
        campaign = campaignRepository.save(campaign);

        // Fire the async send only AFTER this transaction commits. Otherwise the executor's own
        // findById can run on its pool thread before the SENDING row is visible, find nothing, and
        // leave the campaign stuck in SENDING. Outside a transaction (unit tests) run it inline.
        triggerSendAfterCommit(id);
        log.info("Instagram campaign {} queued for async send", id);
        return InstagramCampaignResponse.from(campaign);
    }

    private void triggerSendAfterCommit(Long campaignId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.executeCampaign(campaignId);
                }
            });
        } else {
            executor.executeCampaign(campaignId);
        }
    }

    @Transactional(readOnly = true)
    public Page<InstagramCampaignResponse> list(Pageable pageable) {
        Long tenant = restaurantAuthorizationService.currentTenantReadScopeStrict();
        Page<InstagramCampaign> page = (tenant == null)
                ? campaignRepository.findAll(pageable)                            // SUPER_ADMIN
                : campaignRepository.findByRestaurantIdOrderByIdDesc(tenant, pageable);
        return page.map(InstagramCampaignResponse::from);
    }

    @Transactional(readOnly = true)
    public InstagramCampaignResponse getCampaign(Long id) {
        return InstagramCampaignResponse.from(findForCallerOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<InstagramCampaignRecipientResponse> getRecipients(Long id, Pageable pageable) {
        findForCallerOrThrow(id);   // tenant check: a foreign campaign id reads as not-found
        return recipientRepository.findByCampaignId(id, pageable)
                .map(InstagramCampaignRecipientResponse::from);
    }

    // ------------------------------------------------------------------ helpers

    private InstagramCampaign findForCallerOrThrow(Long id) {
        Long tenant = restaurantAuthorizationService.currentTenantReadScopeStrict();
        Optional<InstagramCampaign> campaign = (tenant == null)
                ? campaignRepository.findById(id)
                : campaignRepository.findByIdAndRestaurantId(id, tenant);
        return campaign.orElseThrow(
                () -> new ResourceNotFoundException("Instagram campaign not found: " + id));
    }

    private Long requireWritableTenant() {
        Long restaurantId = restaurantAuthorizationService.currentTenantScopeStrict();
        if (restaurantId == null) {
            throw new BadRequestException(
                    "An Instagram campaign belongs to a restaurant. Sign in with a restaurant-scoped "
                            + "account to create one.");
        }
        return restaurantId;
    }

    private static InstagramCampaignAudience parseAudience(String raw) {
        if (raw == null || raw.isBlank()) {
            return InstagramCampaignAudience.ALL;
        }
        try {
            return InstagramCampaignAudience.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "Unknown target audience: " + raw + " (expected ALL or REGISTERED).");
        }
    }

    private static String defaultName(String name) {
        return (name == null || name.isBlank()) ? "Broadcast" : name.trim();
    }

    /**
     * Blank in, null out — an image is optional (V176), and storing {@code ""} instead of {@code NULL}
     * would leave an empty string sitting in the {@code image_url} column while still tripping
     * {@code InstagramCampaignExecutor}'s "does this campaign have a photo" check the same way null
     * does, so there is no behavioural reason to keep it. Trimmed and length-checked against the
     * {@code VARCHAR(500)} column so an oversized URL fails fast with a clear 400 here rather than a
     * raw driver exception at insert time.
     */
    private static String normalizeImageUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 500) {
            throw new BadRequestException("Image URL must be at most 500 characters.");
        }
        return trimmed;
    }
}

package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.instagram.dto.InstagramConversationMessageResponse;
import com.elcafe.modules.instagram.dto.InstagramConversationResponse;
import com.elcafe.modules.instagram.dto.InstagramConversationSummaryResponse;
import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.dto.InstagramSubscriberResponse;
import com.elcafe.modules.instagram.entity.InstagramInboundMessage;
import com.elcafe.modules.instagram.entity.InstagramLog;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.repository.InstagramInboundMessageRepository;
import com.elcafe.modules.instagram.repository.InstagramLogRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The read/write side of the Instagram agent-takeover inbox (V179): "what did this customer say", and
 * letting a human agent claim a subscriber's thread away from the registration wizard for a while.
 *
 * <p>A <b>conversation</b> is one subscriber: every inbound message that carries a {@code subscriber_id}
 * (see {@link InstagramInboundMessage}'s javadoc for when it legitimately does not — a stranger's very
 * first message being a STOP/SUBSCRIBE keyword) belongs to exactly one subscriber's thread, and that is
 * the unit this service lists and opens. A message recorded with no subscriber yet has no thread to
 * belong to, so it does not surface in {@link #listConversations} — a deliberate, narrow limitation (see
 * class-level judgment calls in the delivering task's report).
 *
 * <p>Storage ({@link InstagramInboundMessage}) is owned by {@link InstagramWebhookService}; the
 * take-over/release flag ({@link InstagramSubscriber#getHumanHandoffUntil()}) is written here and read
 * by the webhook. Deliberately does NOT touch {@code InstagramBotService} — {@link #reply} is the one
 * write this service delegates to it, reusing {@code sendAdminMessage} exactly as the existing admin-DM
 * endpoint does, rather than duplicating its tenant-scoped send/log logic.
 */
@Slf4j
@Service
public class InstagramInboxService {

    private final InstagramSubscriberRepository subscriberRepository;
    private final InstagramInboundMessageRepository inboundMessageRepository;
    private final InstagramLogRepository logRepository;
    private final InstagramBotService botService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    /**
     * Default take-over duration, configurable per {@code instagram.inbox.default-handoff-hours}
     * (mirrors the {@code @Value}-configurable style already used throughout this module, e.g. {@code
     * instagram.campaign.messaging-window-hours}). 2 hours is a judgment call: long enough to cover one
     * human agent's active back-and-forth with a customer, short enough that a forgotten release does
     * not permanently silence the wizard for that subscriber.
     */
    private final long defaultHandoffHours;

    /** A caller MAY request a longer take-over via {@link #takeover}'s optional {@code hours} — capped
     *  here (30 days) purely so a nonsensical value cannot overflow {@link OffsetDateTime#plusHours}. */
    private static final long MAX_HANDOFF_HOURS = 24L * 30;

    public InstagramInboxService(InstagramSubscriberRepository subscriberRepository,
                                 InstagramInboundMessageRepository inboundMessageRepository,
                                 InstagramLogRepository logRepository,
                                 InstagramBotService botService,
                                 RestaurantAuthorizationService restaurantAuthorizationService,
                                 @Value("${instagram.inbox.default-handoff-hours:2}") long defaultHandoffHours) {
        this.subscriberRepository = subscriberRepository;
        this.inboundMessageRepository = inboundMessageRepository;
        this.logRepository = logRepository;
        this.botService = botService;
        this.restaurantAuthorizationService = restaurantAuthorizationService;
        this.defaultHandoffHours = defaultHandoffHours;
    }

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    /**
     * Recent conversations for the caller's restaurant, newest-inbound-first, each with a preview of
     * the latest message. Requires a restaurant-scoped caller — like {@code
     * InstagramStatisticsService#getStatistics}, a platform (SUPER_ADMIN) account has no single inbox of
     * its own to list, so it gets a 400 rather than an all-zero or ill-defined cross-tenant response.
     *
     * <p>Three queries regardless of page size, not one-plus-N: the id/ordering query, a batch {@code
     * findAllById} for the subscribers, and a batch "most recent rows for these subscriber ids" query
     * for previews (grouped back down to one-per-subscriber in Java, since the DB round-trip is what is
     * worth avoiding, not the trivial in-memory grouping after it).
     */
    @Transactional(readOnly = true)
    public Page<InstagramConversationSummaryResponse> listConversations(Pageable pageable) {
        Long tenant = requireTenant();

        Page<Long> subscriberIds = inboundMessageRepository.findRecentConversationSubscriberIds(tenant, pageable);
        List<Long> ids = subscriberIds.getContent();
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, subscriberIds.getTotalElements());
        }

        Map<Long, InstagramSubscriber> subscribersById = subscriberRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(InstagramSubscriber::getId, s -> s));

        // Ordered newest-received-first per row; the merge function keeps the FIRST row encountered per
        // subscriber id, which — given that ordering — is the most recent one. A subscriber id absent
        // here (should not happen: every id came from a GROUP BY over this same table) simply gets no
        // preview rather than failing the whole listing.
        Map<Long, InstagramInboundMessage> previewById = inboundMessageRepository
                .findByRestaurantIdAndSubscriberIdInOrderByReceivedAtDesc(tenant, ids).stream()
                .collect(Collectors.toMap(m -> m.getSubscriber().getId(), m -> m, (first, later) -> first));

        List<InstagramConversationSummaryResponse> content = ids.stream()
                .map(id -> toSummary(subscribersById.get(id), previewById.get(id)))
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(content, pageable, subscriberIds.getTotalElements());
    }

    private static InstagramConversationSummaryResponse toSummary(InstagramSubscriber subscriber,
                                                                   InstagramInboundMessage preview) {
        if (subscriber == null) {
            return null; // defensive only — see listConversations' javadoc; should not occur in practice
        }
        return InstagramConversationSummaryResponse.builder()
                .subscriberId(subscriber.getId())
                .igsid(subscriber.getIgsid())
                .username(subscriber.getUsername())
                .displayName(subscriber.getDisplayName())
                .conversationState(subscriber.getConversationState())
                .isBlocked(subscriber.getIsBlocked())
                .preview(preview != null ? preview.getMessageText() : null)
                .lastMessageAt(preview != null ? preview.getReceivedAt() : null)
                .humanHandoffUntil(subscriber.getHumanHandoffUntil())
                .build();
    }

    /**
     * One conversation's full, merged transcript: every inbound {@link InstagramInboundMessage} plus
     * that subscriber's outbound {@link InstagramLog} rows (the wizard's automated replies, admin DMs,
     * campaign sends this subscriber received), sorted chronologically oldest-first. Tenant-scoped via
     * {@link #findSubscriberOrThrow} — a foreign subscriber id reads as not-found, same as every other
     * per-id Instagram admin action in this module.
     */
    @Transactional(readOnly = true)
    public InstagramConversationResponse getConversation(Long subscriberId) {
        InstagramSubscriber subscriber = findSubscriberOrThrow(subscriberId);

        List<InstagramConversationMessageResponse> merged = new ArrayList<>();
        inboundMessageRepository
                .findByRestaurantIdAndSubscriberIdOrderByReceivedAtAsc(subscriber.getRestaurantId(), subscriberId)
                .forEach(m -> merged.add(InstagramConversationMessageResponse.builder()
                        .direction(InstagramConversationMessageResponse.Direction.IN)
                        .text(m.getMessageText())
                        .timestamp(m.getReceivedAt())
                        .build()));
        logRepository.findBySubscriberIdOrderByCreatedAtDesc(subscriberId)
                .forEach(l -> merged.add(InstagramConversationMessageResponse.builder()
                        .direction(InstagramConversationMessageResponse.Direction.OUT)
                        .text(l.getMessage())
                        .timestamp(l.getCreatedAt())
                        .messageType(l.getMessageType() != null ? l.getMessageType().name() : null)
                        .status(l.getStatus() != null ? l.getStatus().name() : null)
                        .build()));

        merged.sort(Comparator.comparing(InstagramConversationMessageResponse::getTimestamp,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return InstagramConversationResponse.builder()
                .subscriber(InstagramSubscriberResponse.from(subscriber))
                .messages(merged)
                .build();
    }

    // -------------------------------------------------------------------------
    // Writes
    // -------------------------------------------------------------------------

    /**
     * Post an agent's reply. A thin, deliberate pass-through to {@code InstagramBotService
     * #sendAdminMessage} — the exact same send/log path {@code InstagramSubscriberController}'s
     * {@code /send} endpoint already uses — so tenant scoping, the outbound {@link InstagramLog} row,
     * and V175 token-health tracking all stay in the one place that already implements them correctly.
     * Does NOT itself touch {@link InstagramSubscriber#getHumanHandoffUntil()}: replying does not
     * implicitly extend or start a take-over, which stays an explicit {@link #takeover}/{@link
     * #release} action (see the delivering task's report for this judgment call).
     */
    public InstagramSendResult reply(Long subscriberId, String text) {
        return botService.sendAdminMessage(subscriberId, text);
    }

    /**
     * Claim a subscriber's thread for a human agent: {@link InstagramWebhookService} will keep storing
     * inbound messages but stop dispatching them to the wizard until this lapses or {@link #release}
     * clears it. {@code requestedHoursOrNull} lets an agent ask for something other than the configured
     * default (e.g. a longer shift); null/non-positive falls back to {@link #defaultHandoffHours}, and
     * anything past {@link #MAX_HANDOFF_HOURS} is clamped rather than rejected — a typo'd huge number
     * should not fail the whole request when "a long time" is a perfectly reasonable interpretation.
     */
    @Transactional
    public InstagramSubscriberResponse takeover(Long subscriberId, Long requestedHoursOrNull) {
        InstagramSubscriber subscriber = findSubscriberOrThrow(subscriberId);
        long hours = resolveHandoffHours(requestedHoursOrNull);
        subscriber.setHumanHandoffUntil(OffsetDateTime.now(ZoneOffset.UTC).plusHours(hours));
        InstagramSubscriber saved = subscriberRepository.save(subscriber);
        log.info("Instagram subscriber {} handed off to a human agent until {} (restaurant {})",
                subscriberId, saved.getHumanHandoffUntil(), saved.getRestaurantId());
        return InstagramSubscriberResponse.from(saved);
    }

    /** Release a take-over — the wizard resumes answering this subscriber immediately. A no-op save
     *  (not an error) when nothing was handed off, mirroring {@code
     *  InstagramBotService#unlinkSubscriberFromCustomer}'s identical "undo of a no-op is still a
     *  success" stance. */
    @Transactional
    public InstagramSubscriberResponse release(Long subscriberId) {
        InstagramSubscriber subscriber = findSubscriberOrThrow(subscriberId);
        subscriber.setHumanHandoffUntil(null);
        InstagramSubscriber saved = subscriberRepository.save(subscriber);
        log.info("Instagram subscriber {} released back to the wizard (restaurant {})",
                subscriberId, saved.getRestaurantId());
        return InstagramSubscriberResponse.from(saved);
    }

    private long resolveHandoffHours(Long requestedHoursOrNull) {
        if (requestedHoursOrNull == null || requestedHoursOrNull <= 0) {
            return defaultHandoffHours;
        }
        return Math.min(requestedHoursOrNull, MAX_HANDOFF_HOURS);
    }

    // -------------------------------------------------------------------------
    // Tenant scoping — mirrors InstagramBotService#findSubscriberForCallerOrThrow /
    // InstagramStatisticsService#requireTenant exactly, since InstagramBotService's own helper is
    // private and this service is deliberately independent of it (see class javadoc).
    // -------------------------------------------------------------------------

    private InstagramSubscriber findSubscriberOrThrow(Long id) {
        Long tenant = restaurantAuthorizationService.currentTenantReadScopeStrict();
        Optional<InstagramSubscriber> subscriber = (tenant == null)
                ? subscriberRepository.findById(id)
                : subscriberRepository.findByIdAndRestaurantId(id, tenant);
        return subscriber.orElseThrow(
                () -> new ResourceNotFoundException("Instagram subscriber not found: " + id));
    }

    private Long requireTenant() {
        Long tenant = restaurantAuthorizationService.currentTenantReadScopeStrict();
        if (tenant == null) {
            throw new BadRequestException(
                    "The Instagram inbox belongs to a restaurant. Sign in with a restaurant-scoped "
                            + "account to view it.");
        }
        return tenant;
    }
}

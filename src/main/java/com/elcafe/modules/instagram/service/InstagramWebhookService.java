package com.elcafe.modules.instagram.service;

import com.elcafe.common.tenant.TenantContext;
import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramInboundMessage;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.enums.InstagramInboundKind;
import com.elcafe.modules.instagram.enums.InstagramMessageType;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import com.elcafe.modules.instagram.repository.InstagramInboundMessageRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import com.elcafe.modules.promotion.dto.CouponCodeResponse;
import com.elcafe.modules.promotion.dto.GenerateCouponsRequest;
import com.elcafe.modules.promotion.repository.PromotionRepository;
import com.elcafe.modules.promotion.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Processes incoming Meta webhook payloads for the Instagram integration.
 *
 * Supported event types:
 *  - messages       → DM text or quick-reply from a user
 *  - messaging_postbacks → quick-reply postbacks
 *  - comments       → comment on a business post (triggers auto-reply if enabled)
 *
 * <p><b>Tenancy (V163).</b> The webhook is an unauthenticated, public endpoint, so it carries no
 * {@link TenantContext} of its own. The tenant is derived from the payload's {@code entry.id} — the
 * Instagram business account that received the event — which maps to exactly one
 * {@link InstagramBotConfig} ({@code uq_ig_config_account}). Every entry is processed under the
 * config it resolves to; an entry for an unknown account is dropped rather than guessed at.
 *
 * <p><b>Conversation storage + human handoff (V179).</b> {@link #processMessagingEvent} is also where
 * every inbound TEXT message is durably stored ({@link #recordInboundTextBestEffort}, best-effort —
 * see its javadoc) and where a subscriber currently claimed by a human agent
 * ({@code InstagramInboxService#takeover}, {@link InstagramSubscriber#getHumanHandoffUntil()}) has the
 * wizard dispatch — but never the storage — skipped ({@link #isHandedOffNow}). Both live entirely in
 * this class, not {@code InstagramBotService}: that service's conversation flow is out of scope for
 * this change (a later feature rewrites it), so handoff is enforced purely as a gate in front of its
 * one entry point, {@code handleIncomingMessage}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramWebhookService {

    private final InstagramBotService botService;
    private final InstagramApiClient  apiClient;
    private final InstagramWebhookDedupService dedupService;

    /** Best-effort audit trail for the public auto-reply and the private reply — see
     *  {@link #processChangeEvent}. */
    private final InstagramMessageLogger messageLogger;

    /** Mints the coupon substituted into a private reply's {@code {code}} placeholder — see
     *  {@link #mintCouponCodeOrNull}. */
    private final CouponService couponService;

    /** Ownership check for {@code privateReplyPromotionId} before minting — see
     *  {@link #mintCouponCodeOrNull}. */
    private final PromotionRepository promotionRepository;

    /** Targeted {@code last_webhook_received_at} stamp (V177) — see {@link #stampLastWebhookReceivedBestEffort}. */
    private final InstagramBotConfigRepository configRepository;

    /** V179: the sender's existing subscriber row, read for both the human-handoff check and the
     *  inbound-message subscriber link — see {@link #findSubscriberBestEffort}. */
    private final InstagramSubscriberRepository subscriberRepository;

    /** V179: durable inbound-message storage — see {@link #recordInboundTextBestEffort}. */
    private final InstagramInboundMessageRepository inboundMessageRepository;

    /**
     * Namespacing for the dedup key so a numeric comment id can never collide with a message/postback
     * {@code mid} in the shared {@code instagram_processed_events.event_id} column.
     */
    private static final String MID_PREFIX     = "msg:";
    private static final String COMMENT_PREFIX = "cmt:";

    // -------------------------------------------------------------------------
    // Signature verification
    // -------------------------------------------------------------------------

    /**
     * Verify Meta's {@code X-Hub-Signature-256} header (HMAC-SHA256 of the raw body under the app
     * secret of the config that owns the receiving account).
     *
     * <p><b>Fails closed.</b> A missing config, a missing app secret, or a missing/short header all
     * return {@code false}. The previous implementation returned {@code true} when no app secret was
     * configured, which made this the only fail-open webhook receiver in the codebase — anyone who
     * could reach the endpoint could forge events for any Instagram user. Activating a config now
     * requires an app secret ({@code InstagramBotConfigService}), so a live integration always has
     * something to verify against.
     *
     * @param rawBody the exact bytes received, never a re-encoded String — Meta signs the byte stream
     */
    public boolean verifySignature(byte[] rawBody, String signatureHeader, InstagramBotConfig config) {
        if (config == null || config.getAppSecret() == null || config.getAppSecret().isBlank()) {
            log.error("Instagram webhook rejected: no app secret configured for the receiving account");
            return false;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            log.warn("Instagram webhook rejected: missing or malformed X-Hub-Signature-256 header");
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.getAppSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(rawBody);
            String expected = "sha256=" + HexFormat.of().formatHex(digest);
            // Constant-time: a byte-by-byte early exit leaks how much of a forged signature was right.
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HMAC verification error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * The Instagram business account id a payload is addressed to, taken from the first entry.
     * A single Meta delivery always belongs to one app/account, so this identifies the tenant whose
     * app secret must verify the signature.
     */
    public String resolveAccountId(Map<String, Object> payload) {
        if (payload == null) return null;
        List<Map<String, Object>> entries = castList(payload.get("entry"));
        if (entries == null || entries.isEmpty()) return null;
        Object id = entries.get(0).get("id");
        return id != null ? id.toString() : null;
    }

    // -------------------------------------------------------------------------
    // Webhook event dispatch
    // -------------------------------------------------------------------------

    /**
     * Entry point called from the controller (runs on a background thread via @Async).
     *
     * @param payload deserialized JSON body as nested Map
     */
    @Async
    public void processWebhookPayload(Map<String, Object> payload) {
        try {
            String object = (String) payload.get("object");
            if (!"instagram".equalsIgnoreCase(object)) {
                log.debug("Ignoring non-instagram webhook object: {}", object);
                return;
            }

            List<Map<String, Object>> entries = castList(payload.get("entry"));
            if (entries == null) return;

            for (Map<String, Object> entry : entries) {
                processEntry(entry);
            }
        } catch (Exception e) {
            log.error("Error processing Instagram webhook payload: {}", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolve the entry's owning config (and therefore its tenant) before touching any data, then
     * bind {@link TenantContext} for the duration so this async thread's writes are tenant-attributed
     * — the {@code TenantInsertGuard} vetoes a cross-tenant insert, and every log line carries the
     * tenant id. Cleared in {@code finally} so nothing leaks onto the next task on this pooled thread.
     */
    private void processEntry(Map<String, Object> entry) {
        Object accountId = entry.get("id");
        InstagramBotConfig config = botService.getConfigByInstagramAccountId(
                accountId != null ? accountId.toString() : null);
        if (config == null) {
            log.warn("Instagram webhook entry for unknown account {} — dropped", accountId);
            return;
        }
        if (!Boolean.TRUE.equals(config.getIsActive())) {
            log.debug("Instagram config for account {} is inactive — entry dropped", accountId);
            return;
        }

        // V177: a genuine, processed entry under a live config — record it BEFORE dispatch, and in its
        // own try/catch, so a DB blip stamping the heartbeat can never be the reason the actual
        // messaging/comment payload below goes unprocessed (see the method's own javadoc).
        stampLastWebhookReceivedBestEffort(config);

        TenantContext.setRestaurantId(config.getRestaurantId());
        try {
            List<Map<String, Object>> messaging = castList(entry.get("messaging"));
            if (messaging != null) {
                for (Map<String, Object> event : messaging) {
                    processMessagingEvent(config, event);
                }
            }

            List<Map<String, Object>> changes = castList(entry.get("changes"));
            if (changes != null) {
                for (Map<String, Object> change : changes) {
                    processChangeEvent(config, change);
                }
            }
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Best-effort "Meta is delivering webhooks" heartbeat (V177) for the connection-test/health UI
     * ({@code InstagramBotConfigController#testConnection} covers the token-validity half; this covers
     * "is anything even reaching us"). Recorded once per PROCESSED entry — not once per individual
     * messaging/comment item inside it, since a single delivery can legitimately carry many and this is
     * a coarse "we heard from Meta just now" signal, not a per-message audit trail ({@code
     * InstagramLog} already is that). Deliberately unreachable from Meta's GET hub-challenge handshake
     * ({@code InstagramWebhookController#verify}, which never calls into this class at all): a
     * successful challenge only proves the verify token matches, not that Meta is actually delivering
     * real events, so only a genuine POST entry — this method's one caller — should move the needle.
     *
     * <p>Uses a targeted {@link InstagramBotConfigRepository#updateLastWebhookReceivedAt} column update
     * instead of loading the whole entity and calling {@code save()}: {@code config} here already came
     * from {@link InstagramBotService#getConfigByInstagramAccountId}, and re-saving it on every single
     * inbound webhook would needlessly re-run the {@code EncryptedStringConverter} over the access
     * token / app secret columns for a field that is not changing.
     *
     * <p>Own try/catch: a failure here (a DB blip) must never propagate out of {@link #processEntry} —
     * that method's caller, {@link #processWebhookPayload}, wraps its whole per-delivery loop in one
     * try/catch, so an uncaught exception stamping this heartbeat would abort processing of the actual
     * messaging/comment payload for this entry AND every later entry in the same delivery.
     */
    private void stampLastWebhookReceivedBestEffort(InstagramBotConfig config) {
        try {
            configRepository.updateLastWebhookReceivedAt(config.getId(), OffsetDateTime.now());
        } catch (Exception e) {
            log.warn("Failed to stamp Instagram last-webhook-received for config id={}: {}",
                    config.getId(), e.getMessage(), e);
        }
    }

    /**
     * Classify one messaging event, then dispatch it.
     *
     * <p>Everything Meta can deliver on this edge arrives in the same envelope, so the event is
     * identified ONCE here and each kind gets its own answer. Events the bot has no business acting
     * on — echoes of our own messages, read and delivery receipts, reactions — are dropped before the
     * bot is called. Unrecognised shapes are logged at debug rather than silently discarded, because
     * the previous silent return is what made "the bot ignored me" impossible to diagnose.
     */
    private void processMessagingEvent(InstagramBotConfig config, Map<String, Object> event) {
        try {
            Map<String, Object> sender = castMap(event.get("sender"));
            if (sender == null) return;
            String senderIgsid = (String) sender.get("id");
            if (senderIgsid == null) return;

            // Try to get username from sender info (usually not present in basic webhook)
            String username = (String) sender.getOrDefault("username", null);

            // --- Non-message events: acknowledge and stop. ---
            if (event.get("read") != null) {
                log.debug("Instagram read receipt from {} — ignored", senderIgsid);
                return;
            }
            if (event.get("delivery") != null) {
                log.debug("Instagram delivery receipt from {} — ignored", senderIgsid);
                return;
            }
            if (event.get("reaction") != null) {
                // A heart on a message is engagement, not an instruction; feeding it to the wizard
                // would consume the step the customer is actually on.
                log.debug("Instagram reaction from {} — ignored", senderIgsid);
                return;
            }

            // --- Postbacks (persistent menu / button taps) carry a payload, never text. ---
            Map<String, Object> postback = castMap(event.get("postback"));
            if (postback != null) {
                String payload = (String) postback.get("payload");
                if (payload != null
                        && dedupService.firstDelivery(config.getRestaurantId(),
                                dedupKey(MID_PREFIX, postback.get("mid")))) {
                    dispatchUnlessHandedOff(config, senderIgsid, username,
                            InstagramInboundKind.QUICK_REPLY, null, payload);
                }
                return;
            }

            Map<String, Object> message = castMap(event.get("message"));
            if (message == null) {
                log.debug("Instagram messaging event with no message/postback from {} — ignored", senderIgsid);
                return;
            }

            // Echoes are the messages OUR page sent (bot replies, or a human agent in the IG
            // inbox). Meta delivers them with sender.id = the business account; processing one
            // would create a phantom subscriber and make the bot answer itself.
            if (Boolean.TRUE.equals(message.get("is_echo"))) {
                log.debug("Ignoring echo of our own Instagram message");
                return;
            }

            // Meta re-delivers on any non-2xx/timeout; a repeated mid must not re-advance the wizard or
            // re-run the customer link. Recorded before dispatch, with the unique constraint arbitrating
            // concurrent re-deliveries. Every dispatch branch below rides on this same message mid.
            if (!dedupService.firstDelivery(config.getRestaurantId(),
                    dedupKey(MID_PREFIX, message.get("mid")))) {
                return;
            }

            String text = (String) message.get("text");

            // A quick-reply bubble rides on a message but is a payload, not typed input.
            Map<String, Object> quickReply = castMap(message.get("quick_reply"));
            if (quickReply != null && quickReply.get("payload") != null) {
                dispatchUnlessHandedOff(config, senderIgsid, username,
                        InstagramInboundKind.QUICK_REPLY, text, (String) quickReply.get("payload"));
                return;
            }

            // Story mention: the business account was tagged in someone's story.
            if (hasAttachmentOfType(message, "story_mention")) {
                dispatchUnlessHandedOff(config, senderIgsid, username,
                        InstagramInboundKind.STORY_MENTION, text, null);
                return;
            }

            // Story reply: Meta marks it with reply_to.story. Usually an emoji, and the highest-volume
            // DM source for a restaurant account — it must never be read as wizard input.
            Map<String, Object> replyTo = castMap(message.get("reply_to"));
            if (replyTo != null && replyTo.get("story") != null) {
                dispatchUnlessHandedOff(config, senderIgsid, username,
                        InstagramInboundKind.STORY_REPLY, text, null);
                return;
            }

            // Any other attachment (photo, video, audio, file, location, shared post, sticker) is
            // something the wizard cannot parse.
            if (hasAnyAttachment(message)) {
                dispatchUnlessHandedOff(config, senderIgsid, username,
                        InstagramInboundKind.UNSUPPORTED_ATTACHMENT, text, null);
                return;
            }

            if (text != null) {
                // V179: store the customer's own words BEFORE the handoff-gated dispatch below, and
                // regardless of its outcome — a human agent taking over must not blind the transcript,
                // and a storage hiccup (own try/catch inside recordInboundTextBestEffort) must never
                // cost the wizard's turn. One subscriber lookup, reused for both this and the dispatch
                // gate rather than querying twice for the same sender.
                InstagramSubscriber subscriber = findSubscriberBestEffort(config, senderIgsid);
                recordInboundTextBestEffort(config, senderIgsid, subscriber, text);
                dispatchUnlessHandedOff(config, senderIgsid, username,
                        InstagramInboundKind.TEXT, text, null, subscriber);
                return;
            }

            log.debug("Unrecognised Instagram message shape from {} (keys={}) — ignored",
                    senderIgsid, message.keySet());
        } catch (Exception e) {
            log.error("Error processing Instagram messaging event: {}", e.getMessage(), e);
        }
    }

    private static boolean hasAnyAttachment(Map<String, Object> message) {
        List<Map<String, Object>> attachments = castList(message.get("attachments"));
        return attachments != null && !attachments.isEmpty();
    }

    private static boolean hasAttachmentOfType(Map<String, Object> message, String type) {
        List<Map<String, Object>> attachments = castList(message.get("attachments"));
        if (attachments == null) return false;
        return attachments.stream()
                .filter(Objects::nonNull)
                .anyMatch(a -> type.equals(a.get("type")));
    }

    // -------------------------------------------------------------------------
    // V179: conversation storage + human handoff. See the class javadoc's "Conversation storage +
    // human handoff" paragraph for how these fit into processMessagingEvent above.
    // -------------------------------------------------------------------------

    /**
     * Resolve the sender's existing subscriber row for one messaging event, or {@code null} for a
     * brand-new sender (the wizard may go on to create one — see {@code
     * InstagramBotService#startRegistration}) or a stranger whose only message ever is a STOP/SUBSCRIBE
     * keyword (deliberately never given a subscriber row — see {@code InstagramBotService#handleOptOut}
     * / {@code #handleOptIn}). Reused for both the human-handoff check ({@link #isHandedOffNow}) and,
     * for a TEXT message, the stored row's {@code subscriber_id} link — one lookup, not two.
     *
     * <p>Best-effort: a lookup failure (or, in the narrow case of an existing unit test that mocks this
     * class without wiring {@link #subscriberRepository}, a null repository) resolves to {@code null}
     * exactly like "no subscriber row found" — the caller then treats the sender as not handed off,
     * which is the safe default (see {@link #isHandedOffNow}).
     */
    private InstagramSubscriber findSubscriberBestEffort(InstagramBotConfig config, String igsid) {
        try {
            return subscriberRepository.findByIgsidAndRestaurantId(igsid, config.getRestaurantId()).orElse(null);
        } catch (Exception e) {
            log.warn("Could not look up Instagram subscriber {} (restaurant {}): {} — treating as "
                    + "unknown/not-handed-off", igsid, config.getRestaurantId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * True when {@code subscriber} is currently claimed by a human agent (V179's
     * {@code InstagramInboxService#takeover}) — the wizard must not answer on their behalf until it
     * lapses or {@code InstagramInboxService#release} clears it. {@code null} (no subscriber row, or
     * the lookup above failed) is never handed off: there is nothing to hand off yet, and defaulting to
     * "the wizard answers" on an unrelated DB hiccup is the safer failure mode than silently
     * blackholing the customer's message with no reply at all.
     */
    private static boolean isHandedOffNow(InstagramSubscriber subscriber) {
        return subscriber != null
                && subscriber.getHumanHandoffUntil() != null
                && subscriber.getHumanHandoffUntil().isAfter(OffsetDateTime.now());
    }

    /**
     * Dispatch to the registration wizard unless the sender is currently handed off (see {@link
     * #isHandedOffNow}). Every {@code botService.handleIncomingMessage} call in this class goes through
     * one of these two overloads so the handoff gate cannot be bypassed by a future call site forgetting
     * to check it. This one resolves the subscriber itself; {@link
     * #dispatchUnlessHandedOff(InstagramBotConfig, String, String, InstagramInboundKind, String, String,
     * InstagramSubscriber)} takes an already-resolved one instead, for the TEXT call site that also
     * needs it for storage.
     */
    private void dispatchUnlessHandedOff(InstagramBotConfig config, String senderIgsid, String username,
                                         InstagramInboundKind kind, String text, String quickReplyPayload) {
        dispatchUnlessHandedOff(config, senderIgsid, username, kind, text, quickReplyPayload,
                findSubscriberBestEffort(config, senderIgsid));
    }

    /** As {@link #dispatchUnlessHandedOff(InstagramBotConfig, String, String, InstagramInboundKind,
     *  String, String)}, but reusing an already-resolved subscriber rather than looking it up again. */
    private void dispatchUnlessHandedOff(InstagramBotConfig config, String senderIgsid, String username,
                                         InstagramInboundKind kind, String text, String quickReplyPayload,
                                         InstagramSubscriber subscriber) {
        if (isHandedOffNow(subscriber)) {
            log.debug("Instagram {} from {} (restaurant {}) suppressed — handed off to a human agent "
                    + "until {}", kind, senderIgsid, config.getRestaurantId(),
                    subscriber.getHumanHandoffUntil());
            return;
        }
        botService.handleIncomingMessage(config, senderIgsid, username, kind, text, quickReplyPayload);
    }

    /**
     * Best-effort persistence of one inbound TEXT message — the substrate the Instagram inbox
     * ({@code InstagramInboxService}) reads "what did this customer say" from. Deliberately narrower
     * than every dispatch call site above: only a typed message (kind {@code TEXT}, this method's one
     * caller) is stored, not a quick-reply button tap, a story engagement, or an unsupported-attachment
     * placeholder — none of those are "what the customer said" in the sense an inbox transcript needs.
     *
     * <p>Stored regardless of {@link #isHandedOffNow}: handoff only ever suppresses the WIZARD's
     * answer, never the record of the inbound message itself — the whole point of the inbox is to let a
     * human agent read what came in while (or before) they were handling it.
     *
     * <p>Own try/catch, independent of every other one in this class: a storage failure (a DB blip)
     * must never be the reason the wizard dispatch that follows does not run, mirroring {@link
     * #stampLastWebhookReceivedBestEffort}'s identical stance on the V177 heartbeat.
     */
    private void recordInboundTextBestEffort(InstagramBotConfig config, String igsid,
                                             InstagramSubscriber subscriberOrNull, String text) {
        try {
            inboundMessageRepository.save(InstagramInboundMessage.builder()
                    .restaurantId(config.getRestaurantId())
                    .subscriber(subscriberOrNull)
                    .igsid(igsid)
                    .messageText(text)
                    .receivedAt(OffsetDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to store inbound Instagram message from {} (restaurant {}): {}",
                    igsid, config.getRestaurantId(), e.getMessage(), e);
        }
    }

    /**
     * Handles a {@code comments} change event: the public auto-reply (V105) and the private-reply DM
     * (V173) are independent features — a config can run either, both, or neither — but they share ONE
     * dedup check below, since {@link InstagramWebhookDedupService#firstDelivery} is check-and-record:
     * calling it twice for the same key within one invocation would make the second call see "already
     * processed" and silently swallow whichever action runs second. That single check is also what
     * caps the private reply's coupon mint at one per comment even under Meta's at-least-once
     * redelivery — see {@link #mintCouponCodeOrNull}.
     */
    private void processChangeEvent(InstagramBotConfig config, Map<String, Object> change) {
        try {
            String field = (String) change.get("field");
            if (!"comments".equals(field)) return;

            Map<String, Object> value = castMap(change.get("value"));
            if (value == null) return;

            String commentId   = (String) value.get("id");
            String commentText = (String) value.get("text");
            if (commentId == null) return;

            // Check it's not an echo of our own reply
            Boolean fromMe = (Boolean) value.get("from_me");
            if (Boolean.TRUE.equals(fromMe)) return;

            boolean autoReplyEnabled = Boolean.TRUE.equals(config.getAutoReplyEnabled());
            String autoReplyTemplate = config.getAutoReplyTemplate();
            boolean autoReplyWanted = autoReplyEnabled
                    && autoReplyTemplate != null && !autoReplyTemplate.isBlank();
            if (autoReplyEnabled && !autoReplyWanted) {
                log.debug("Auto-reply enabled but no template set, skipping comment {}", commentId);
            }

            boolean privateReplyWanted = Boolean.TRUE.equals(config.getPrivateReplyEnabled())
                    && matchesPrivateReplyKeyword(config.getPrivateReplyKeyword(), commentText)
                    && config.getPrivateReplyTemplate() != null && !config.getPrivateReplyTemplate().isBlank();

            if (!autoReplyWanted && !privateReplyWanted) return;

            // See method javadoc: this one call gates BOTH actions below.
            if (!dedupService.firstDelivery(config.getRestaurantId(), dedupKey(COMMENT_PREFIX, commentId))) {
                return;
            }

            if (autoReplyWanted) {
                sendPublicAutoReply(config, commentId, commentText, autoReplyTemplate);
            }
            if (privateReplyWanted) {
                sendPrivateReplyForComment(config, commentId, commentText);
            }
        } catch (Exception e) {
            log.error("Error processing Instagram change event: {}", e.getMessage(), e);
        }
    }

    private void sendPublicAutoReply(InstagramBotConfig config, String commentId, String commentText,
                                      String replyTemplate) {
        // Basic placeholder replacement
        String reply = replyTemplate
                .replace("{comment}", commentText != null ? commentText : "")
                .replace("{comment_text}", commentText != null ? commentText : "");

        log.info("Auto-replying to comment {}", commentId);
        InstagramSendResult result = apiClient.replyToComment(config, commentId, reply);
        // A comment reply is public, not a DM — there is no subscriber/igsid recipient, so the
        // comment id is carried in the igsid column as the identifier this log row concerns.
        messageLogger.record(config, commentId, null, InstagramMessageType.AUTO_REPLY,
                reply, result, null);
    }

    /**
     * Meta's private-reply endpoint ("comment {keyword} and we'll DM you"): opens a fresh 24h DM window
     * against the commenter, independent of the public auto-reply above. When the config names a
     * promotion, mints ONE single-use coupon code and substitutes it into {@code {code}}; otherwise —
     * or if minting fails for any reason — the placeholder is stripped and the DM still goes out: the
     * newly-opened messaging window has value on its own, and a coupon-mint hiccup should not also cost
     * the send. BEST-EFFORT throughout, matching every other send path in this class: nothing here
     * escapes to {@link #processChangeEvent}'s catch as anything but a log line.
     */
    private void sendPrivateReplyForComment(InstagramBotConfig config, String commentId, String commentText) {
        String code = mintCouponCodeOrNull(config, commentId);
        String message = config.getPrivateReplyTemplate().replace("{code}", code != null ? code : "");

        log.info("Sending Instagram private reply for comment {}", commentId);
        InstagramSendResult result = apiClient.sendPrivateReply(config, commentId, message);
        // A private reply targets a comment, not an existing DM thread — there is no subscriber/igsid
        // recipient yet, so (like AUTO_REPLY) the comment id fills the igsid slot.
        messageLogger.record(config, commentId, null, InstagramMessageType.PRIVATE_REPLY,
                message, result, null);
    }

    /**
     * Mint one coupon for the config's configured promotion, or {@code null} when no promotion is set
     * or minting fails for any reason (unknown/foreign promotion, code-generator exhaustion, ...) — the
     * caller falls back to the {@code {code}}-stripped template rather than dropping the send.
     *
     * <p>Ownership is checked explicitly against {@code config.getRestaurantId()}
     * ({@link PromotionRepository#existsByIdAndRestaurant_Id}) rather than trusted to the
     * {@code restaurantFilter} Hibernate filter alone: that filter only actively restricts rows in
     * ENFORCE mode ({@code app.security.tenant-enforcement.mode}, "shadow" by default), so without this
     * check a config could mint codes against another restaurant's promotion under the current default.
     *
     * <p>Called at most once per comment: {@link #processChangeEvent} only reaches here after its dedup
     * check passes, and Meta's at-least-once redelivery of the same comment is exactly what that check
     * exists to collapse — so a comment mints at most one code, never one per redelivery.
     */
    private String mintCouponCodeOrNull(InstagramBotConfig config, String commentId) {
        Long promotionId = config.getPrivateReplyPromotionId();
        if (promotionId == null) {
            return null;
        }
        try {
            if (!promotionRepository.existsByIdAndRestaurant_Id(promotionId, config.getRestaurantId())) {
                log.warn("Instagram private-reply promotion {} not found for restaurant {} (comment {}) "
                        + "— sending without a code", promotionId, config.getRestaurantId(), commentId);
                return null;
            }
            List<CouponCodeResponse> minted = couponService.generateCoupons(GenerateCouponsRequest.builder()
                    .promotionId(promotionId)
                    .count(1)
                    .build());
            return minted.isEmpty() ? null : minted.get(0).getCode();
        } catch (Exception e) {
            log.warn("Instagram private-reply coupon mint failed for promotion {} (comment {}): {}",
                    promotionId, commentId, e.getMessage());
            return null;
        }
    }

    /**
     * Case-insensitive CONTAINS match, not equals: a "comment {keyword} and we'll DM you" post invites
     * free-form text ("menu pls!", "🙋 MENU", "can I get the MENU"), so requiring the comment to be
     * exactly the keyword would miss most real comments. A blank/unset keyword never matches —
     * {@code privateReplyEnabled} alone must not fire a DM on every comment.
     */
    private static boolean matchesPrivateReplyKeyword(String keyword, String commentText) {
        if (keyword == null || keyword.isBlank() || commentText == null) {
            return false;
        }
        return commentText.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    /**
     * Namespaced dedup key for a Meta event, or {@code null} when the id is absent — a null key makes
     * {@link InstagramWebhookDedupService#firstDelivery} fail open (process without deduplicating),
     * since a real message or comment always carries an id.
     */
    private static String dedupKey(String prefix, Object metaId) {
        return metaId == null ? null : prefix + metaId;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object o) {
        if (o instanceof List<?>) return (List<Map<String, Object>>) o;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        if (o instanceof Map<?, ?>) return (Map<String, Object>) o;
        return null;
    }
}

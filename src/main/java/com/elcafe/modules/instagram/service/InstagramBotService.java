package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.entity.InstagramSubscriberAddress;
import com.elcafe.modules.instagram.enums.InstagramInboundKind;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberAddressRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the Instagram DM registration wizard and outbound messaging.
 *
 * Conversation states
 * ───────────────────
 *  AWAITING_NAME           → user has not yet provided their name
 *  AWAITING_PHONE          → name collected, awaiting phone number
 *  AWAITING_BIRTHDAY       → phone collected, awaiting birthday (skippable)
 *  AWAITING_ADDRESS        → birthday step done, awaiting delivery address text
 *  AWAITING_MORE_ADDRESSES → first address saved, asking if more needed
 *  REGISTERED              → wizard complete
 *
 * <p><b>Why the DB work and the Graph send are separated.</b> One inbound DM produces at most one
 * outbound reply. Each wizard step now mutates its rows and RETURNS that reply ({@link PendingReply})
 * instead of sending it; {@link #handleIncomingMessage} runs those steps inside a transaction and
 * performs the actual Graph call only after the transaction commits. Sending inside the transaction
 * pinned a Hikari connection for the whole 5s-connect + 10s-read Meta round-trip, so a Meta slowdown
 * drained the pool one webhook thread at a time — an {@code afterCommit} hook would not have helped,
 * because Spring returns the connection only after those hooks run.
 */
@Slf4j
@Service
public class InstagramBotService {

    @Value("${branding.name:Qahvoon}")
    private String brandName;

    private static final String STATE_AWAITING_NAME           = "AWAITING_NAME";
    private static final String STATE_AWAITING_PHONE          = "AWAITING_PHONE";
    private static final String STATE_AWAITING_BIRTHDAY       = "AWAITING_BIRTHDAY";
    private static final String STATE_AWAITING_ADDRESS        = "AWAITING_ADDRESS";
    private static final String STATE_AWAITING_MORE_ADDRESSES = "AWAITING_MORE_ADDRESSES";
    private static final String STATE_REGISTERED              = "REGISTERED";

    /** Ceiling on saved addresses — the wizard had none, so every message became a new row. */
    private static final int MAX_ADDRESSES = 5;
    private static final int MIN_ADDRESS_LENGTH = 6;

    private static final DateTimeFormatter BIRTHDAY_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final List<Map<String, String>> MORE_ADDRESS_QUICK_REPLIES = List.of(
            Map.of("title", "Add another", "payload", "ADD_ADDRESS"),
            Map.of("title", "Done",         "payload", "DONE")
    );

    private final InstagramBotConfigRepository configRepository;
    private final InstagramSubscriberRepository subscriberRepository;
    private final InstagramSubscriberAddressRepository addressRepository;
    private final CustomerRepository customerRepository;
    private final InstagramApiClient apiClient;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    /**
     * The wizard's DB work runs inside this template and the Graph send happens only after it
     * returns (see {@link #handleIncomingMessage}). A plain {@code @Transactional} could not express
     * that boundary: it keeps the pooled connection checked out until the method exits, which for the
     * wizard meant across the entire Meta HTTP round-trip. Built from the app's single
     * {@link PlatformTransactionManager}, so it is the same tenant-aware manager every
     * {@code @Transactional} uses — the {@code restaurantFilter} is enabled at {@code doBegin} exactly
     * as before.
     */
    private final TransactionTemplate txTemplate;

    public InstagramBotService(InstagramBotConfigRepository configRepository,
                               InstagramSubscriberRepository subscriberRepository,
                               InstagramSubscriberAddressRepository addressRepository,
                               CustomerRepository customerRepository,
                               InstagramApiClient apiClient,
                               RestaurantAuthorizationService restaurantAuthorizationService,
                               PlatformTransactionManager transactionManager) {
        this.configRepository = configRepository;
        this.subscriberRepository = subscriberRepository;
        this.addressRepository = addressRepository;
        this.customerRepository = customerRepository;
        this.apiClient = apiClient;
        this.restaurantAuthorizationService = restaurantAuthorizationService;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    // -------------------------------------------------------------------------
    // Incoming message handler (called from webhook service)
    // -------------------------------------------------------------------------

    /**
     * Handle one inbound DM.
     *
     * <p>V163: the config is resolved by the caller from the webhook's {@code entry.id} (the IG
     * business account that received the event) and passed in, because it carries the tenant. It is
     * NOT looked up here — the webhook runs on an {@code @Async} thread with no {@link
     * com.elcafe.common.tenant.TenantContext}, so every subscriber read/write below scopes itself
     * explicitly to {@code config.getRestaurantId()}.
     *
     * <p>The DB work runs in {@link #txTemplate} and yields the one reply to send; the Graph call is
     * made here, after commit, so no pooled connection is held across the HTTP round-trip.
     */
    public void handleIncomingMessage(InstagramBotConfig config, String senderIgsid, String username,
                                      InstagramInboundKind kind, String text, String quickReplyPayload) {
        if (config == null) return;
        PendingReply reply = txTemplate.execute(status ->
                process(config, senderIgsid, username, kind, text, quickReplyPayload));
        dispatch(config, reply);
    }

    /**
     * The transactional core: resolve the subscriber, run one wizard step, and RETURN the reply to
     * send rather than sending it. Runs entirely inside {@link #txTemplate}, so it must not perform
     * any network I/O — that is {@link #dispatch}'s job, once this has committed.
     */
    private PendingReply process(InstagramBotConfig config, String senderIgsid, String username,
                                 InstagramInboundKind kind, String text, String quickReplyPayload) {
        Long restaurantId = config.getRestaurantId();

        Optional<InstagramSubscriber> existing =
                subscriberRepository.findByIgsidAndRestaurantId(senderIgsid, restaurantId);

        // A blocked subscriber is done talking to the bot. This check comes BEFORE the restart
        // keyword: previously "hi" from a blocked account restarted the wizard and sent a fresh
        // welcome DM, so blocking greyed the row out in the admin UI while changing nothing.
        if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getIsBlocked())) {
            log.debug("Ignoring Instagram message from blocked subscriber {}", senderIgsid);
            return null;
        }

        // Story engagement is never wizard input. A "🔥" on a story used to be persisted as a
        // delivery address, and a first-time story replier was dragged into registration.
        if (kind == InstagramInboundKind.STORY_REPLY || kind == InstagramInboundKind.STORY_MENTION) {
            return handleStoryEngagement(config, existing.orElse(null), senderIgsid, kind);
        }

        // Always (re-)start wizard on "hi" / "start" keywords or if subscriber is new
        if (existing.isEmpty() || (kind == InstagramInboundKind.TEXT && isRestartKeyword(text))) {
            return startRegistration(config, senderIgsid, username);
        }

        InstagramSubscriber subscriber = existing.get();
        subscriber.touch();

        String state = subscriber.getConversationState();
        if (state == null) state = STATE_AWAITING_NAME;

        // Quick-reply button took priority
        if (kind == InstagramInboundKind.QUICK_REPLY && quickReplyPayload != null) {
            return handleQuickReply(subscriber, quickReplyPayload, state);
        }

        // Media the wizard cannot read: say what we are waiting for instead of going silent, which
        // is what left customers parked mid-wizard with no idea the bot wanted something else.
        if (kind == InstagramInboundKind.UNSUPPORTED_ATTACHMENT) {
            subscriberRepository.save(subscriber);
            return rePromptForState(subscriber, state);
        }

        return switch (state) {
            case STATE_AWAITING_NAME           -> handleNameInput(subscriber, text);
            case STATE_AWAITING_PHONE          -> handlePhoneInput(subscriber, text);
            case STATE_AWAITING_BIRTHDAY       -> handleBirthdayInput(subscriber, text);
            case STATE_AWAITING_ADDRESS        -> handleAddressInput(subscriber, text);
            case STATE_AWAITING_MORE_ADDRESSES -> handleMoreAddressesInput(subscriber, text);
            case STATE_REGISTERED              -> sendMainMenu(subscriber);
            default                            -> startRegistration(config, senderIgsid, username);
        };
    }

    /**
     * A story reply or mention. It is engagement, not an answer to whatever the wizard asked, so it
     * never advances or consumes a wizard step. A story reply from someone we have never met is also
     * NOT a reason to start a registration wizard at them.
     */
    private PendingReply handleStoryEngagement(InstagramBotConfig config, InstagramSubscriber subscriber,
                                               String igsid, InstagramInboundKind kind) {
        log.info("Instagram {} from {} (restaurant {})", kind, igsid, config.getRestaurantId());
        if (subscriber == null) {
            return null;
        }
        subscriber.touch();
        subscriberRepository.save(subscriber);
        // Mid-wizard: remind them what we were waiting for, so the thread does not just stop.
        String state = subscriber.getConversationState();
        if (state != null && !STATE_REGISTERED.equals(state)) {
            return rePromptForState(subscriber, state);
        }
        return null;
    }

    /** Re-state the current wizard question. Mirrors what the Telegram bot already does. */
    private PendingReply rePromptForState(InstagramSubscriber subscriber, String state) {
        String prompt = switch (state) {
            case STATE_AWAITING_NAME     -> "Ismingizni matn ko'rinishida yuboring:";
            case STATE_AWAITING_PHONE    -> "Telefon raqamingizni matn ko'rinishida yuboring (masalan +998901234567):";
            case STATE_AWAITING_BIRTHDAY -> "Tug'ilgan kuningizni kiriting (kun.oy.yil) yoki /skip deb yozing:";
            case STATE_AWAITING_ADDRESS,
                 STATE_AWAITING_MORE_ADDRESSES -> "Manzilni matn ko'rinishida yozing:";
            default -> null;
        };
        return prompt == null ? null : PendingReply.text(subscriber.getIgsid(), prompt);
    }

    // -------------------------------------------------------------------------
    // Wizard steps — each returns the one reply to send (or null); none sends itself
    // -------------------------------------------------------------------------

    private PendingReply startRegistration(InstagramBotConfig config, String igsid, String username) {
        InstagramSubscriber subscriber = subscriberRepository
                .findByIgsidAndRestaurantId(igsid, config.getRestaurantId())
                .orElseGet(() -> InstagramSubscriber.builder()
                        .restaurantId(config.getRestaurantId())
                        .igsid(igsid)
                        .subscribedAt(OffsetDateTime.now(ZoneOffset.UTC))
                        .isActive(true)
                        .isBlocked(false)
                        .build());

        // Instagram usually omits username on the webhook, and overwriting with null on every
        // restart wiped whatever we had already learned.
        if (username != null && !username.isBlank()) {
            subscriber.setUsername(username);
        }
        subscriber.setConversationState(STATE_AWAITING_NAME);
        subscriber.touch();
        subscriberRepository.save(subscriber);

        String welcome = (config.getWelcomeMessage() != null && !config.getWelcomeMessage().isBlank())
                ? config.getWelcomeMessage() + "\n\n"
                : "👋 Xush kelibsiz " + brandName + "'ga!\n\n";
        return PendingReply.text(igsid, welcome + "Ismingizni kiriting (to'liq ism yoki laqab):");
    }

    private PendingReply handleNameInput(InstagramSubscriber subscriber, String text) {
        String name = text.trim();
        if (name.length() < 2 || name.length() > 100) {
            return PendingReply.text(subscriber.getIgsid(),
                    "Iltimos, to'liq ismingizni kiriting (2–100 belgi):");
        }
        subscriber.setDisplayName(name);
        subscriber.setConversationState(STATE_AWAITING_PHONE);
        subscriberRepository.save(subscriber);
        return PendingReply.text(subscriber.getIgsid(),
                "Juda yaxshi, " + name + "! 😊\n\nTelefon raqamingizni kiriting (masalan: +998901234567):");
    }

    private PendingReply handlePhoneInput(InstagramSubscriber subscriber, String text) {
        String phone = normalizePhone(text.trim());
        if (phone.replaceAll("[^\\d]", "").length() < 7) {
            return PendingReply.text(subscriber.getIgsid(),
                    "❌ To'g'ri telefon raqam kiriting (masalan: +998901234567):");
        }
        subscriber.setPhone(phone);
        subscriber.setConversationState(STATE_AWAITING_BIRTHDAY);
        subscriberRepository.save(subscriber);
        return PendingReply.text(subscriber.getIgsid(),
                "📅 Tug'ilgan kuningizni kiriting (DD.MM.YYYY, masalan: 15.03.1990)\n\nO'tkazib yuborish uchun: /skip");
    }

    private PendingReply handleBirthdayInput(InstagramSubscriber subscriber, String text) {
        if (!text.trim().equals("/skip")) {
            try {
                subscriber.setBirthDate(LocalDate.parse(text.trim(), BIRTHDAY_FMT));
            } catch (DateTimeParseException e) {
                return PendingReply.text(subscriber.getIgsid(),
                        "❌ Format noto'g'ri. DD.MM.YYYY ko'rinishida kiriting yoki /skip yozing:");
            }
        }
        subscriber.setConversationState(STATE_AWAITING_ADDRESS);
        subscriberRepository.save(subscriber);
        return PendingReply.text(subscriber.getIgsid(),
                "📍 Yetkazib berish manzilingizni yozing (ko'cha, uy raqami, mo'ljal):\n\nO'tkazib yuborish: /skip");
    }

    private PendingReply handleAddressInput(InstagramSubscriber subscriber, String text) {
        if (text == null || text.isBlank()) {
            return rePromptForState(subscriber, STATE_AWAITING_ADDRESS);
        }
        String address = text.trim();
        if (address.equals("/skip")) {
            return completeRegistration(subscriber);
        }
        // An address the wizard stores must at least look like one. Without this, "ok" and "thanks"
        // became delivery addresses.
        if (address.length() < MIN_ADDRESS_LENGTH) {
            return PendingReply.text(subscriber.getIgsid(),
                    "Manzil juda qisqa. To'liq manzilni yozing (ko'cha, uy raqami, mo'ljal)"
                            + " yoki tugatish uchun /skip.");
        }

        long existing = addressRepository.countBySubscriber(subscriber);
        if (existing >= MAX_ADDRESSES) {
            return PendingReply.text(subscriber.getIgsid(), "Sizda allaqachon " + MAX_ADDRESSES
                    + " ta manzil saqlangan. Tugatish uchun \"Done\" tugmasini bosing.");
        }
        // Case-insensitive duplicate check: re-registering used to append the same address again.
        boolean duplicate = addressRepository.findAllBySubscriber(subscriber).stream()
                .anyMatch(a -> a.getAddress() != null && a.getAddress().trim().equalsIgnoreCase(address));
        if (duplicate) {
            subscriber.setConversationState(STATE_AWAITING_MORE_ADDRESSES);
            subscriberRepository.save(subscriber);
            return PendingReply.withQuickReplies(subscriber.getIgsid(),
                    "Bu manzil allaqachon saqlangan.\n\nYana manzil qo'shmoqchimisiz?",
                    MORE_ADDRESS_QUICK_REPLIES);
        }
        InstagramSubscriberAddress addr = InstagramSubscriberAddress.builder()
                .restaurantId(subscriber.getRestaurantId())
                .subscriber(subscriber)
                .address(address)
                .isDefault(existing == 0)
                .build();
        addressRepository.save(addr);

        subscriber.setConversationState(STATE_AWAITING_MORE_ADDRESSES);
        subscriberRepository.save(subscriber);

        return PendingReply.withQuickReplies(subscriber.getIgsid(),
                "✅ Manzil saqlandi! (Jami: " + (existing + 1) + " ta)\n\nYana manzil qo'shmoqchimisiz?",
                MORE_ADDRESS_QUICK_REPLIES);
    }

    /**
     * In AWAITING_MORE_ADDRESSES the user is answering a yes/no question posed via quick replies.
     * Typed text used to be stored as another address, so "yo'q" ("no") became a delivery address.
     */
    private PendingReply handleMoreAddressesInput(InstagramSubscriber subscriber, String text) {
        String answer = text == null ? "" : text.trim().toLowerCase();
        if (answer.equals("/skip") || answer.equals("yo'q") || answer.equals("yoq")
                || answer.equals("no") || answer.equals("done") || answer.equals("tugatish")) {
            return completeRegistration(subscriber);
        }
        if (answer.equals("ha") || answer.equals("yes") || answer.equals("qo'shish")) {
            subscriber.setConversationState(STATE_AWAITING_ADDRESS);
            subscriberRepository.save(subscriber);
            return PendingReply.text(subscriber.getIgsid(), "📍 Yangi manzilni yozing:");
        }
        // Anything else is treated as another address — the useful default here, but only after the
        // yes/no answers above have been taken out.
        return handleAddressInput(subscriber, text);
    }

    /**
     * Quick-reply payloads are gated on the states they belong to. Postback bubbles stay tappable
     * forever in Instagram, so an old "Done" could otherwise jump a half-finished wizard straight to
     * REGISTERED.
     */
    private PendingReply handleQuickReply(InstagramSubscriber subscriber, String payload, String state) {
        boolean addressStage = STATE_AWAITING_ADDRESS.equals(state)
                || STATE_AWAITING_MORE_ADDRESSES.equals(state);
        return switch (payload) {
            case "ADD_ADDRESS" -> {
                if (!addressStage) {
                    yield rePromptForState(subscriber, state);
                }
                subscriber.setConversationState(STATE_AWAITING_ADDRESS);
                subscriberRepository.save(subscriber);
                yield PendingReply.text(subscriber.getIgsid(), "📍 Yangi manzilni yozing:");
            }
            case "DONE" -> {
                if (!addressStage) {
                    yield rePromptForState(subscriber, state);
                }
                yield completeRegistration(subscriber);
            }
            default -> sendMainMenu(subscriber);
        };
    }

    private PendingReply completeRegistration(InstagramSubscriber subscriber) {
        // Never mark a profile REGISTERED without the fields the wizard exists to collect: a
        // subscriber with a null phone used to reach REGISTERED and then sit in every campaign
        // audience as an un-contactable row.
        if (subscriber.getDisplayName() == null || subscriber.getDisplayName().isBlank()) {
            subscriber.setConversationState(STATE_AWAITING_NAME);
            subscriberRepository.save(subscriber);
            return rePromptForState(subscriber, STATE_AWAITING_NAME);
        }
        if (subscriber.getPhone() == null || subscriber.getPhone().isBlank()) {
            subscriber.setConversationState(STATE_AWAITING_PHONE);
            subscriberRepository.save(subscriber);
            return rePromptForState(subscriber, STATE_AWAITING_PHONE);
        }

        // Try to link to an existing customer by phone.
        // V163: the bot is per-tenant now, so link to THIS restaurant's customer record rather than
        // the global oldest-row-for-the-phone (customers are per-restaurant since V150).
        if (subscriber.getPhone() != null && subscriber.getCustomer() == null) {
            customerRepository
                    .findByPhoneAndRestaurantId(subscriber.getPhone(), subscriber.getRestaurantId())
                    .ifPresent(customer -> {
                        subscriber.setCustomer(customer);
                        log.info("Linked Instagram subscriber {} to customer {} (restaurant {})",
                                subscriber.getIgsid(), customer.getId(), subscriber.getRestaurantId());
                    });
        }
        subscriber.setConversationState(STATE_REGISTERED);
        subscriberRepository.save(subscriber);
        log.info("Instagram subscriber registered: igsid={}", subscriber.getIgsid());

        List<InstagramSubscriberAddress> addresses = addressRepository.findAllBySubscriber(subscriber);

        StringBuilder sb = new StringBuilder();
        sb.append("🎉 Ro'yxatdan o'tish yakunlandi!\n\n");
        sb.append("📋 Sizning ma'lumotlaringiz:\n");
        sb.append("👤 ").append(subscriber.getDisplayNameOrFallback()).append("\n");
        sb.append("📱 ").append(subscriber.getPhone() != null ? subscriber.getPhone() : "kiritilmagan").append("\n");
        sb.append("🎂 ").append(subscriber.getBirthDate() != null
                ? subscriber.getBirthDate().format(BIRTHDAY_FMT) : "kiritilmagan").append("\n");
        if (!addresses.isEmpty()) {
            sb.append("📍 Manzillar: ").append(addresses.size()).append(" ta\n");
        }
        if (subscriber.getCustomer() != null) {
            sb.append("\n✨ Hisobingiz mijoz profili bilan bog'landi!\n");
        }
        sb.append("\n🎁 Endi siz aksiyalar va tug'ilgan kun sovg'alari haqida xabar olasiz!");
        return PendingReply.text(subscriber.getIgsid(), sb.toString());
    }

    private PendingReply sendMainMenu(InstagramSubscriber subscriber) {
        return PendingReply.text(subscriber.getIgsid(),
                "👋 Salom, " + subscriber.getDisplayNameOrFallback() + "!\n\n" +
                "Ma'lumotlarni yangilash uchun \"hi\" yoki \"start\" yozing.");
    }

    /**
     * Perform the single Graph send a wizard turn asked for. Called from {@link #handleIncomingMessage}
     * AFTER {@link #txTemplate} has committed, so the pooled DB connection is already back in the pool
     * — the Meta round-trip never holds one.
     */
    private void dispatch(InstagramBotConfig config, PendingReply reply) {
        if (reply == null) {
            return;
        }
        if (reply.quickReplies() != null) {
            apiClient.sendMessageWithQuickReplies(config, reply.igsid(), reply.text(), reply.quickReplies());
        } else {
            apiClient.sendMessage(config, reply.igsid(), reply.text());
        }
    }

    // -------------------------------------------------------------------------
    // Admin reads — scoped to the caller's restaurant so one tenant never sees another's
    // subscriber PII (name, phone, birth date).
    // -------------------------------------------------------------------------

    public org.springframework.data.domain.Page<InstagramSubscriber> listSubscribers(
            org.springframework.data.domain.Pageable pageable) {
        Long tenant = restaurantAuthorizationService.currentTenantReadScopeStrict();
        return (tenant == null)
                ? subscriberRepository.findAll(pageable)                       // SUPER_ADMIN
                : subscriberRepository.findByRestaurantIdAndIsActiveTrue(tenant, pageable);
    }

    public org.springframework.data.domain.Page<InstagramSubscriber> searchSubscribers(
            String query, org.springframework.data.domain.Pageable pageable) {
        Long tenant = restaurantAuthorizationService.currentTenantReadScopeStrict();
        if (tenant == null) {
            // A platform account has no subscriber list of its own; searching across tenants would
            // be the cross-tenant PII sweep V163 removes.
            throw new com.elcafe.exception.BadRequestException(
                    "Instagram subscribers belong to a restaurant. Sign in with a restaurant-scoped "
                            + "account to search them.");
        }
        return subscriberRepository.search(tenant, query, pageable);
    }

    /** Tenant-scoped single-subscriber read. */
    public InstagramSubscriber getSubscriber(Long id) {
        return findSubscriberForCallerOrThrow(id);
    }

    // -------------------------------------------------------------------------
    // Admin DM controls — every lookup is scoped to the caller's restaurant, so a guessed id
    // belonging to another tenant reads as not-found rather than acting on their subscriber.
    // -------------------------------------------------------------------------

    @Transactional
    public InstagramSubscriber blockSubscriber(Long id) {
        InstagramSubscriber s = findSubscriberForCallerOrThrow(id);
        s.setIsBlocked(true);
        return subscriberRepository.save(s);
    }

    @Transactional
    public InstagramSubscriber unblockSubscriber(Long id) {
        InstagramSubscriber s = findSubscriberForCallerOrThrow(id);
        s.setIsBlocked(false);
        return subscriberRepository.save(s);
    }

    /**
     * Admin sends a DM to one subscriber by their DB id. Returns the full send outcome so the
     * controller can tell the operator WHY a message did not go out — the old boolean made a dead
     * token and a blocked recipient look identical.
     */
    public InstagramSendResult sendAdminMessage(Long id, String text) {
        InstagramSubscriber s = findSubscriberForCallerOrThrow(id);
        InstagramBotConfig config = getActiveConfig(s.getRestaurantId());
        if (config == null) {
            log.warn("No active Instagram config for restaurant {} — cannot DM subscriber {}",
                    s.getRestaurantId(), id);
            return InstagramSendResult.failed(
                    InstagramSendResult.Failure.INVALID_REQUEST, 0, "no active Instagram configuration");
        }
        return apiClient.sendMessage(config, s.getIgsid(), text);
    }

    /**
     * Broadcast a text message to the caller's own subscribers.
     *
     * @param text    message to send
     * @param target  "ALL" = all active non-blocked; "REGISTERED" = only fully registered ones
     * @return number of messages successfully delivered
     */
    public int broadcast(String text, String target) {
        Long restaurantId = requireCallerTenant();
        InstagramBotConfig config = getActiveConfig(restaurantId);
        if (config == null) {
            log.warn("No active Instagram config for restaurant {} — broadcast skipped", restaurantId);
            return 0;
        }
        List<InstagramSubscriber> recipients = "REGISTERED".equalsIgnoreCase(target)
                ? subscriberRepository.findAllRegistered(restaurantId)
                : subscriberRepository.findAllActiveNotBlocked(restaurantId);

        int sent = 0;
        int failed = 0;
        InstagramSendResult.Failure stoppedBy = null;
        for (InstagramSubscriber s : recipients) {
            InstagramSendResult result = apiClient.sendMessage(config, s.getIgsid(), text);
            if (result.delivered()) {
                sent++;
                continue;
            }
            failed++;
            // A dead token or a rate-limit dooms the rest of the run — there is no point firing the
            // remaining thousands of calls at Meta. Everything else (blocked recipient, transient)
            // is per-message: skip it and keep going.
            if (result.failure() == InstagramSendResult.Failure.TOKEN_INVALID
                    || result.failure() == InstagramSendResult.Failure.RATE_LIMITED
                    || result.failure() == InstagramSendResult.Failure.CIRCUIT_OPEN) {
                stoppedBy = result.failure();
                break;
            }
        }
        if (stoppedBy != null) {
            log.warn("Instagram broadcast for restaurant {} halted after {} sent / {} failed: {}",
                    restaurantId, sent, failed, stoppedBy);
        } else {
            log.info("Instagram broadcast sent to {}/{} recipients (restaurant {})",
                    sent, recipients.size(), restaurantId);
        }
        return sent;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** The active Instagram config of one restaurant, or null when that restaurant has none. */
    public InstagramBotConfig getActiveConfig(Long restaurantId) {
        if (restaurantId == null) return null;
        return configRepository.findByRestaurantIdAndIsActiveTrue(restaurantId).orElse(null);
    }

    /** Resolve the config that owns an inbound webhook, keyed by the receiving IG business account. */
    public InstagramBotConfig getConfigByInstagramAccountId(String instagramAccountId) {
        if (instagramAccountId == null || instagramAccountId.isBlank()) return null;
        return configRepository.findByInstagramAccountId(instagramAccountId).orElse(null);
    }

    /**
     * Resolve the config for Meta's GET hub-challenge, which carries only {@code hub.verify_token}.
     * The token is a high-entropy per-restaurant secret, so a match identifies the tenant; the value
     * is re-compared in constant time so the handshake does not leak a prefix through timing.
     */
    public InstagramBotConfig getConfigByVerifyToken(String verifyToken) {
        if (verifyToken == null || verifyToken.isBlank()) return null;
        return configRepository.findByVerifyToken(verifyToken)
                .filter(c -> c.getVerifyToken() != null && java.security.MessageDigest.isEqual(
                        c.getVerifyToken().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        verifyToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .orElse(null);
    }

    private InstagramSubscriber findSubscriberForCallerOrThrow(Long id) {
        Long tenant = restaurantAuthorizationService.currentTenantReadScopeStrict();
        Optional<InstagramSubscriber> subscriber = (tenant == null)
                ? subscriberRepository.findById(id)
                : subscriberRepository.findByIdAndRestaurantId(id, tenant);
        return subscriber.orElseThrow(
                () -> new ResourceNotFoundException("Instagram subscriber not found: " + id));
    }

    /**
     * The restaurant whose subscribers the caller may act on. A platform account has no subscriber
     * list of its own — broadcasting "as the platform" across tenants is exactly the cross-tenant
     * blast V163 removes.
     */
    private Long requireCallerTenant() {
        Long restaurantId = restaurantAuthorizationService.currentTenantScopeStrict();
        if (restaurantId == null) {
            throw new com.elcafe.exception.BadRequestException(
                    "Instagram subscribers belong to a restaurant. Sign in with a restaurant-scoped "
                            + "account to message them.");
        }
        return restaurantId;
    }

    private boolean isRestartKeyword(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.equals("start") || t.equals("hi") || t.equals("hello")
                || t.equals("boshlash") || t.equals("/start");
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String d = phone.replaceAll("[^\\d+]", "");
        return d.startsWith("+") ? d : "+" + d;
    }

    /**
     * The one outbound reply a wizard turn produces, captured so the Graph send can happen after the
     * DB transaction commits rather than while it holds a connection. A {@code null} PendingReply
     * means send nothing (a blocked sender, a story reply from a stranger, a no-op state).
     */
    private record PendingReply(String igsid, String text, List<Map<String, String>> quickReplies) {
        static PendingReply text(String igsid, String text) {
            return new PendingReply(igsid, text, null);
        }

        static PendingReply withQuickReplies(String igsid, String text,
                                             List<Map<String, String>> quickReplies) {
            return new PendingReply(igsid, text, quickReplies);
        }
    }

}

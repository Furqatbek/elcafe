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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Slf4j
@Service
@RequiredArgsConstructor
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
     */
    @Transactional
    public void handleIncomingMessage(InstagramBotConfig config, String senderIgsid, String username,
                                      InstagramInboundKind kind, String text, String quickReplyPayload) {
        if (config == null) return;
        Long restaurantId = config.getRestaurantId();

        Optional<InstagramSubscriber> existing =
                subscriberRepository.findByIgsidAndRestaurantId(senderIgsid, restaurantId);

        // A blocked subscriber is done talking to the bot. This check comes BEFORE the restart
        // keyword: previously "hi" from a blocked account restarted the wizard and sent a fresh
        // welcome DM, so blocking greyed the row out in the admin UI while changing nothing.
        if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getIsBlocked())) {
            log.debug("Ignoring Instagram message from blocked subscriber {}", senderIgsid);
            return;
        }

        // Story engagement is never wizard input. A "🔥" on a story used to be persisted as a
        // delivery address, and a first-time story replier was dragged into registration.
        if (kind == InstagramInboundKind.STORY_REPLY || kind == InstagramInboundKind.STORY_MENTION) {
            handleStoryEngagement(config, existing.orElse(null), senderIgsid, kind);
            return;
        }

        // Always (re-)start wizard on "hi" / "start" keywords or if subscriber is new
        if (existing.isEmpty() || (kind == InstagramInboundKind.TEXT && isRestartKeyword(text))) {
            startRegistration(config, senderIgsid, username);
            return;
        }

        InstagramSubscriber subscriber = existing.get();
        subscriber.touch();

        String state = subscriber.getConversationState();
        if (state == null) state = STATE_AWAITING_NAME;

        // Quick-reply button took priority
        if (kind == InstagramInboundKind.QUICK_REPLY && quickReplyPayload != null) {
            handleQuickReply(config, subscriber, quickReplyPayload, state);
            return;
        }

        // Media the wizard cannot read: say what we are waiting for instead of going silent, which
        // is what left customers parked mid-wizard with no idea the bot wanted something else.
        if (kind == InstagramInboundKind.UNSUPPORTED_ATTACHMENT) {
            subscriberRepository.save(subscriber);
            rePromptForState(config, subscriber, state);
            return;
        }

        switch (state) {
            case STATE_AWAITING_NAME           -> handleNameInput(config, subscriber, text);
            case STATE_AWAITING_PHONE          -> handlePhoneInput(config, subscriber, text);
            case STATE_AWAITING_BIRTHDAY       -> handleBirthdayInput(config, subscriber, text);
            case STATE_AWAITING_ADDRESS        -> handleAddressInput(config, subscriber, text);
            case STATE_AWAITING_MORE_ADDRESSES -> handleMoreAddressesInput(config, subscriber, text);
            case STATE_REGISTERED              -> sendMainMenu(config, subscriber);
            default                            -> startRegistration(config, senderIgsid, username);
        }
    }

    /**
     * A story reply or mention. It is engagement, not an answer to whatever the wizard asked, so it
     * never advances or consumes a wizard step. A story reply from someone we have never met is also
     * NOT a reason to start a registration wizard at them.
     */
    private void handleStoryEngagement(InstagramBotConfig config, InstagramSubscriber subscriber,
                                       String igsid, InstagramInboundKind kind) {
        log.info("Instagram {} from {} (restaurant {})", kind, igsid, config.getRestaurantId());
        if (subscriber != null) {
            subscriber.touch();
            subscriberRepository.save(subscriber);
            // Mid-wizard: remind them what we were waiting for, so the thread does not just stop.
            String state = subscriber.getConversationState();
            if (state != null && !STATE_REGISTERED.equals(state)) {
                rePromptForState(config, subscriber, state);
            }
        }
    }

    /** Re-state the current wizard question. Mirrors what the Telegram bot already does. */
    private void rePromptForState(InstagramBotConfig config, InstagramSubscriber subscriber, String state) {
        String prompt = switch (state) {
            case STATE_AWAITING_NAME     -> "Ismingizni matn ko'rinishida yuboring:";
            case STATE_AWAITING_PHONE    -> "Telefon raqamingizni matn ko'rinishida yuboring (masalan +998901234567):";
            case STATE_AWAITING_BIRTHDAY -> "Tug'ilgan kuningizni kiriting (kun.oy.yil) yoki /skip deb yozing:";
            case STATE_AWAITING_ADDRESS,
                 STATE_AWAITING_MORE_ADDRESSES -> "Manzilni matn ko'rinishida yozing:";
            default -> null;
        };
        if (prompt != null) {
            send(config, subscriber, prompt);
        }
    }

    // -------------------------------------------------------------------------
    // Wizard steps
    // -------------------------------------------------------------------------

    private void startRegistration(InstagramBotConfig config, String igsid, String username) {
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
        apiClient.sendMessage(config, igsid, welcome + "Ismingizni kiriting (to'liq ism yoki laqab):");
    }

    private void handleNameInput(InstagramBotConfig config, InstagramSubscriber subscriber, String text) {
        String name = text.trim();
        if (name.length() < 2 || name.length() > 100) {
            send(config, subscriber, "Iltimos, to'liq ismingizni kiriting (2–100 belgi):");
            return;
        }
        subscriber.setDisplayName(name);
        subscriber.setConversationState(STATE_AWAITING_PHONE);
        subscriberRepository.save(subscriber);
        send(config, subscriber,
                "Juda yaxshi, " + name + "! 😊\n\nTelefon raqamingizni kiriting (masalan: +998901234567):");
    }

    private void handlePhoneInput(InstagramBotConfig config, InstagramSubscriber subscriber, String text) {
        String phone = normalizePhone(text.trim());
        if (phone.replaceAll("[^\\d]", "").length() < 7) {
            send(config, subscriber, "❌ To'g'ri telefon raqam kiriting (masalan: +998901234567):");
            return;
        }
        subscriber.setPhone(phone);
        subscriber.setConversationState(STATE_AWAITING_BIRTHDAY);
        subscriberRepository.save(subscriber);
        send(config, subscriber,
                "📅 Tug'ilgan kuningizni kiriting (DD.MM.YYYY, masalan: 15.03.1990)\n\nO'tkazib yuborish uchun: /skip");
    }

    private void handleBirthdayInput(InstagramBotConfig config, InstagramSubscriber subscriber, String text) {
        if (!text.trim().equals("/skip")) {
            try {
                subscriber.setBirthDate(LocalDate.parse(text.trim(), BIRTHDAY_FMT));
            } catch (DateTimeParseException e) {
                send(config, subscriber,
                        "❌ Format noto'g'ri. DD.MM.YYYY ko'rinishida kiriting yoki /skip yozing:");
                return;
            }
        }
        subscriber.setConversationState(STATE_AWAITING_ADDRESS);
        subscriberRepository.save(subscriber);
        send(config, subscriber,
                "📍 Yetkazib berish manzilingizni yozing (ko'cha, uy raqami, mo'ljal):\n\nO'tkazib yuborish: /skip");
    }

    private void handleAddressInput(InstagramBotConfig config, InstagramSubscriber subscriber, String text) {
        if (text == null || text.isBlank()) {
            rePromptForState(config, subscriber, STATE_AWAITING_ADDRESS);
            return;
        }
        String address = text.trim();
        if (address.equals("/skip")) {
            completeRegistration(config, subscriber);
            return;
        }
        // An address the wizard stores must at least look like one. Without this, "ok" and "thanks"
        // became delivery addresses.
        if (address.length() < MIN_ADDRESS_LENGTH) {
            send(config, subscriber,
                    "Manzil juda qisqa. To'liq manzilni yozing (ko'cha, uy raqami, mo'ljal)"
                            + " yoki tugatish uchun /skip.");
            return;
        }

        long existing = addressRepository.countBySubscriber(subscriber);
        if (existing >= MAX_ADDRESSES) {
            send(config, subscriber, "Sizda allaqachon " + MAX_ADDRESSES
                    + " ta manzil saqlangan. Tugatish uchun \"Done\" tugmasini bosing.");
            return;
        }
        // Case-insensitive duplicate check: re-registering used to append the same address again.
        boolean duplicate = addressRepository.findAllBySubscriber(subscriber).stream()
                .anyMatch(a -> a.getAddress() != null && a.getAddress().trim().equalsIgnoreCase(address));
        if (duplicate) {
            subscriber.setConversationState(STATE_AWAITING_MORE_ADDRESSES);
            subscriberRepository.save(subscriber);
            apiClient.sendMessageWithQuickReplies(config, subscriber.getIgsid(),
                    "Bu manzil allaqachon saqlangan.\n\nYana manzil qo'shmoqchimisiz?",
                    MORE_ADDRESS_QUICK_REPLIES);
            return;
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

        apiClient.sendMessageWithQuickReplies(config, subscriber.getIgsid(),
                "✅ Manzil saqlandi! (Jami: " + (existing + 1) + " ta)\n\nYana manzil qo'shmoqchimisiz?",
                MORE_ADDRESS_QUICK_REPLIES);
    }

    /**
     * In AWAITING_MORE_ADDRESSES the user is answering a yes/no question posed via quick replies.
     * Typed text used to be stored as another address, so "yo'q" ("no") became a delivery address.
     */
    private void handleMoreAddressesInput(InstagramBotConfig config, InstagramSubscriber subscriber,
                                          String text) {
        String answer = text == null ? "" : text.trim().toLowerCase();
        if (answer.equals("/skip") || answer.equals("yo'q") || answer.equals("yoq")
                || answer.equals("no") || answer.equals("done") || answer.equals("tugatish")) {
            completeRegistration(config, subscriber);
            return;
        }
        if (answer.equals("ha") || answer.equals("yes") || answer.equals("qo'shish")) {
            subscriber.setConversationState(STATE_AWAITING_ADDRESS);
            subscriberRepository.save(subscriber);
            send(config, subscriber, "📍 Yangi manzilni yozing:");
            return;
        }
        // Anything else is treated as another address — the useful default here, but only after the
        // yes/no answers above have been taken out.
        handleAddressInput(config, subscriber, text);
    }

    /**
     * Quick-reply payloads are gated on the states they belong to. Postback bubbles stay tappable
     * forever in Instagram, so an old "Done" could otherwise jump a half-finished wizard straight to
     * REGISTERED.
     */
    private void handleQuickReply(InstagramBotConfig config, InstagramSubscriber subscriber,
                                  String payload, String state) {
        boolean addressStage = STATE_AWAITING_ADDRESS.equals(state)
                || STATE_AWAITING_MORE_ADDRESSES.equals(state);
        switch (payload) {
            case "ADD_ADDRESS" -> {
                if (!addressStage) { rePromptForState(config, subscriber, state); return; }
                subscriber.setConversationState(STATE_AWAITING_ADDRESS);
                subscriberRepository.save(subscriber);
                send(config, subscriber, "📍 Yangi manzilni yozing:");
            }
            case "DONE" -> {
                if (!addressStage) { rePromptForState(config, subscriber, state); return; }
                completeRegistration(config, subscriber);
            }
            default -> sendMainMenu(config, subscriber);
        }
    }

    private void completeRegistration(InstagramBotConfig config, InstagramSubscriber subscriber) {
        // Never mark a profile REGISTERED without the fields the wizard exists to collect: a
        // subscriber with a null phone used to reach REGISTERED and then sit in every campaign
        // audience as an un-contactable row.
        if (subscriber.getDisplayName() == null || subscriber.getDisplayName().isBlank()) {
            subscriber.setConversationState(STATE_AWAITING_NAME);
            subscriberRepository.save(subscriber);
            rePromptForState(config, subscriber, STATE_AWAITING_NAME);
            return;
        }
        if (subscriber.getPhone() == null || subscriber.getPhone().isBlank()) {
            subscriber.setConversationState(STATE_AWAITING_PHONE);
            subscriberRepository.save(subscriber);
            rePromptForState(config, subscriber, STATE_AWAITING_PHONE);
            return;
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
        send(config, subscriber, sb.toString());
    }

    private void sendMainMenu(InstagramBotConfig config, InstagramSubscriber subscriber) {
        send(config, subscriber,
                "👋 Salom, " + subscriber.getDisplayNameOrFallback() + "!\n\n" +
                "Ma'lumotlarni yangilash uchun \"hi\" yoki \"start\" yozing.");
    }

    // -------------------------------------------------------------------------
    // Public outbound API
    // -------------------------------------------------------------------------

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

    private void send(InstagramBotConfig config, InstagramSubscriber subscriber, String text) {
        apiClient.sendMessage(config, subscriber.getIgsid(), text);
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

}

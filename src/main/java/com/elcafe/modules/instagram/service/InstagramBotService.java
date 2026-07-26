package com.elcafe.modules.instagram.service;

import com.elcafe.common.event.CustomerDeletedEvent;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.entity.InstagramSubscriberAddress;
import com.elcafe.modules.instagram.enums.InstagramInboundKind;
import com.elcafe.modules.instagram.enums.InstagramMessageType;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberAddressRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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
 *
 * <p><b>Opt-out (STOP) / opt-in (V174) precedence.</b> Three keyword families compete for the same
 * inbound text, so {@link #process} checks them in a fixed order, before any wizard state is touched:
 * <ol>
 *   <li>{@link #isOptOutKeyword}  — STOP / UNSUBSCRIBE / "to'xtat" / "bekor" / … Always wins. Checked
 *       before the "{@code existing.isEmpty()} ⇒ start the wizard" branch, so even a total stranger's
 *       very first message being STOP creates no wizard-state row and sends no welcome — it only ever
 *       flips an EXISTING subscriber's {@code marketingOptIn} to false (a stranger has nothing to opt
 *       out of). Never falls through to a wizard step, so STOP is never stored as a name/phone/address.</li>
 *   <li>{@link #isOptInKeyword} minus {@link #isRestartKeyword} — i.e. SUBSCRIBE / "obuna" only. These
 *       are pure consent-renewal keywords with no other meaning in the bot, so they short-circuit the
 *       same way STOP does: flip {@code marketingOptIn} true, confirm, return — never entering the
 *       wizard.</li>
 *   <li>{@link #isRestartKeyword} — "hi" / "start" / "boshlash" / "/start", UNCHANGED: still (re)starts
 *       the registration wizard exactly as before this feature. "start" is deliberately ALSO one of
 *       {@link #isOptInKeyword}'s keywords (the task that added this asked for START specifically), so
 *       rather than pre-empting the restart — which would regress the every-day "type start to update
 *       my info" flow {@link #sendMainMenu} advertises — its opt-in side effect is folded INTO the
 *       restart: {@link #startRegistration} clears any prior opt-out as part of the same save when told
 *       to. "hi"/"boshlash" restart the wizard WITHOUT touching consent: casual re-engagement is not the
 *       same as explicit re-consent to marketing, so only the keywords the task named as opt-in
 *       triggers ever flip the flag.
 * </ol>
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

    /**
     * How many times a wizard turn re-runs when a concurrent delivery for the same subscriber collides
     * with it (see {@link #processWithRetryOnConflict}). Three is generous: the winning transaction
     * commits in milliseconds, so the loser's re-read succeeds on the next attempt in practice.
     */
    private static final int MAX_CONFLICT_ATTEMPTS = 3;

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

    /** Best-effort audit trail for every Graph send this service makes — see {@link #dispatch}. */
    private final InstagramMessageLogger messageLogger;

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
                               InstagramMessageLogger messageLogger,
                               PlatformTransactionManager transactionManager) {
        this.configRepository = configRepository;
        this.subscriberRepository = subscriberRepository;
        this.addressRepository = addressRepository;
        this.customerRepository = customerRepository;
        this.apiClient = apiClient;
        this.restaurantAuthorizationService = restaurantAuthorizationService;
        this.messageLogger = messageLogger;
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
        PendingReply reply =
                processWithRetryOnConflict(config, senderIgsid, username, kind, text, quickReplyPayload);
        dispatch(config, reply);
    }

    /**
     * Run the transactional wizard step, retrying if a concurrent delivery for the SAME subscriber
     * collided with it. Instagram webhooks fan out across the {@code @Async} pool, so two DMs from one
     * sender can execute at once in separate transactions:
     * <ul>
     *   <li>a brand-new sender — both read "no subscriber" and both INSERT, so the loser's commit fails
     *       {@code uq_ig_subscriber_restaurant_igsid} ({@link DataIntegrityViolationException});</li>
     *   <li>an existing sender — both read the same row and both write it back; the {@code @Version}
     *       column (V168) makes the loser fail with {@link OptimisticLockingFailureException} instead of
     *       silently dropping one update.</li>
     * </ul>
     * Either way the loser re-runs in a fresh transaction, re-reading the winner's now-committed state —
     * the serial order the two messages would have had on Telegram's single polling thread — so no
     * message is swallowed by the webhook's blanket catch and no duplicate subscriber is created. The
     * Graph send still happens only after this returns (in {@link #dispatch}), so a retried attempt
     * never double-sends.
     */
    private PendingReply processWithRetryOnConflict(InstagramBotConfig config, String senderIgsid,
                                                    String username, InstagramInboundKind kind,
                                                    String text, String quickReplyPayload) {
        int attempt = 0;
        while (true) {
            try {
                return txTemplate.execute(status ->
                        process(config, senderIgsid, username, kind, text, quickReplyPayload));
            } catch (DataIntegrityViolationException | OptimisticLockingFailureException conflict) {
                if (++attempt >= MAX_CONFLICT_ATTEMPTS) {
                    log.warn("Instagram wizard step for {} lost {} concurrency retries — giving up",
                            senderIgsid, attempt);
                    throw conflict;
                }
                log.debug("Concurrent Instagram delivery for {} (attempt {}) — retrying against "
                        + "committed state", senderIgsid, attempt);
            }
        }
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

        // V174: opt-out ALWAYS wins, checked before ANY wizard dispatch — including the
        // "existing.isEmpty() ⇒ start the wizard" branch below, so a total stranger's first-ever
        // message being STOP is never welcomed into registration, and an in-progress wizard never
        // stores STOP as the answer it happened to be waiting for (a name, a phone number, an
        // address…). See the class javadoc for the full precedence between this, opt-in, and restart.
        if (kind == InstagramInboundKind.TEXT && isOptOutKeyword(text)) {
            return handleOptOut(config, existing.orElse(null), senderIgsid);
        }

        // Pure re-opt-in keywords (SUBSCRIBE / "obuna") also short-circuit before the wizard. "start"
        // is intentionally excluded here even though it is one of isOptInKeyword's words: it is ALSO a
        // restart keyword, and its opt-in side effect is applied inside startRegistration instead (see
        // below) so the restart behaviour that keyword already has stays intact.
        boolean explicitOptIn = kind == InstagramInboundKind.TEXT && isOptInKeyword(text);
        if (explicitOptIn && !isRestartKeyword(text)) {
            return handleOptIn(config, existing.orElse(null), senderIgsid);
        }

        // Always (re-)start wizard on "hi" / "start" keywords or if subscriber is new
        if (existing.isEmpty() || (kind == InstagramInboundKind.TEXT && isRestartKeyword(text))) {
            return startRegistration(config, senderIgsid, username, explicitOptIn);
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
            default                            -> startRegistration(config, senderIgsid, username, false);
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
    // V174: opt-out / opt-in — never wizard input, so neither method advances or reads
    // conversationState. Both are no-ops on the DB (beyond the flag flip) for a subscriber that does
    // not exist yet: a stranger opting out has nothing to unsubscribe from, and a stranger opting in is
    // already the default (marketingOptIn defaults true) — either way, we still reply, since a STOP or
    // SUBSCRIBE deserves an answer whether or not there is a row behind it.
    // -------------------------------------------------------------------------

    private PendingReply handleOptOut(InstagramBotConfig config, InstagramSubscriber subscriber, String igsid) {
        if (subscriber != null) {
            subscriber.setMarketingOptIn(false);
            subscriber.setOptedOutAt(OffsetDateTime.now(ZoneOffset.UTC));
            subscriber.touch();
            subscriberRepository.save(subscriber);
            log.info("Instagram subscriber {} opted out of marketing (restaurant {})",
                    igsid, config.getRestaurantId());
        }
        return PendingReply.text(igsid,
                "Obunangiz bekor qilindi. Endi marketing va aksiya xabarlarini olmaysiz.\n\n"
                        + "Qayta obuna bo'lish uchun START deb yozing.");
    }

    private PendingReply handleOptIn(InstagramBotConfig config, InstagramSubscriber subscriber, String igsid) {
        if (subscriber != null) {
            subscriber.setMarketingOptIn(true);
            subscriber.setOptedOutAt(null);
            subscriber.touch();
            subscriberRepository.save(subscriber);
            log.info("Instagram subscriber {} opted back in to marketing (restaurant {})",
                    igsid, config.getRestaurantId());
        }
        return PendingReply.text(igsid,
                "✅ Siz qayta obuna bo'ldingiz! Marketing va aksiya xabarlarini olasiz.\n\n"
                        + "Xohlagan vaqtda STOP deb yozib bekor qilishingiz mumkin.");
    }

    // -------------------------------------------------------------------------
    // Wizard steps — each returns the one reply to send (or null); none sends itself
    // -------------------------------------------------------------------------

    /**
     * @param reOptIn true when the triggering text was one of {@link #isOptInKeyword}'s words (in
     *                practice, only "start" reaches here with this set — SUBSCRIBE/"obuna" return
     *                earlier via {@link #handleOptIn} without ever calling this). When true and the
     *                subscriber was previously opted out, this also clears that opt-out as part of the
     *                same save and prefixes the reply with a short confirmation — one Graph send still
     *                covers both the re-consent and the wizard welcome.
     */
    private PendingReply startRegistration(InstagramBotConfig config, String igsid, String username,
                                           boolean reOptIn) {
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
        boolean wasOptedOut = reOptIn && Boolean.FALSE.equals(subscriber.getMarketingOptIn());
        if (reOptIn) {
            subscriber.setMarketingOptIn(true);
            subscriber.setOptedOutAt(null);
        }
        subscriber.setConversationState(STATE_AWAITING_NAME);
        subscriber.touch();
        subscriberRepository.save(subscriber);

        String welcome = (config.getWelcomeMessage() != null && !config.getWelcomeMessage().isBlank())
                ? config.getWelcomeMessage() + "\n\n"
                : "👋 Xush kelibsiz " + brandName + "'ga!\n\n";
        if (wasOptedOut) {
            welcome = "✅ Siz qayta obuna bo'ldingiz!\n\n" + welcome;
        }
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
        InstagramSendResult result;
        if (reply.quickReplies() != null) {
            result = apiClient.sendMessageWithQuickReplies(config, reply.igsid(), reply.text(), reply.quickReplies());
        } else {
            result = apiClient.sendMessage(config, reply.igsid(), reply.text());
        }
        // Best-effort audit row for the wizard's automated reply. No subscriber is threaded through
        // PendingReply (it would mean widening every wizard step's return type for this alone), so the
        // log row carries igsid only — never throws, so a logging hiccup cannot turn a real send into
        // an apparent failure.
        messageLogger.record(config, reply.igsid(), null, InstagramMessageType.AUTOMATION,
                reply.text(), result, null);
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
     * Erase one subscriber: their PII (display name, phone, birth date) and every saved address.
     * Tenant-scoped, so a guessed id from another restaurant reads as not-found. This is the
     * right-to-erasure path the channel lacked — blocking only greyed the row out; the data stayed, and
     * nothing could ever purge it.
     */
    @Transactional
    public void deleteSubscriber(Long id) {
        eraseSubscriber(findSubscriberForCallerOrThrow(id));
    }

    /**
     * Erase the Instagram subscribers linked to a customer being deleted. Without this, the customer_id
     * FK merely nulls the link (V105 {@code ON DELETE SET NULL}) and leaves the name/phone/birthday and
     * addresses behind, unreachable by any deletion path. Joins the customer's delete transaction (the
     * event is published synchronously before the customer row is removed), so a failure rolls it all
     * back — never a half-erased customer.
     */
    @EventListener
    @Transactional
    public void onCustomerDeleted(CustomerDeletedEvent event) {
        subscriberRepository.findByCustomerId(event.customerId()).forEach(this::eraseSubscriber);
    }

    private void eraseSubscriber(InstagramSubscriber subscriber) {
        addressRepository.deleteBySubscriber(subscriber);
        messageLogger.eraseSubscriberLogs(subscriber);
        subscriberRepository.delete(subscriber);
        log.info("Erased Instagram subscriber {} (restaurant {})",
                subscriber.getId(), subscriber.getRestaurantId());
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
        InstagramSendResult result = apiClient.sendMessage(config, s.getIgsid(), text);
        messageLogger.record(config, s.getIgsid(), s, InstagramMessageType.MANUAL, text, result, null);
        return result;
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

    private boolean isRestartKeyword(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.equals("start") || t.equals("hi") || t.equals("hello")
                || t.equals("boshlash") || t.equals("/start");
    }

    /**
     * V174: STOP-family keywords — English plus a small Uzbek set. Exact match (like {@link
     * #isRestartKeyword}), not a substring search, so ordinary chat containing the word "stop" is not
     * misread as an opt-out. Both the accented ("to'xtat") and plain-ASCII ("toxtat") spellings are
     * accepted, mirroring how {@code handleMoreAddressesInput} already treats "yo'q"/"yoq" as the same
     * answer — the special apostrophe is awkward to type on a phone keyboard.
     */
    private boolean isOptOutKeyword(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.equals("stop") || t.equals("unsubscribe") || t.equals("cancel")
                || t.equals("to'xtat") || t.equals("toxtat") || t.equals("bekor");
    }

    /**
     * V174: re-opt-in keywords. "start" deliberately overlaps {@link #isRestartKeyword} — see the class
     * javadoc's precedence note and {@link #startRegistration} for how that overlap is resolved.
     */
    private boolean isOptInKeyword(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.equals("start") || t.equals("subscribe") || t.equals("obuna");
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

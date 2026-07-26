package com.elcafe.modules.instagram.service;

import com.elcafe.common.event.CustomerDeletedEvent;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
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
import com.elcafe.modules.instagram.entity.InstagramCartLine;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.modules.restaurant.entity.BusinessHours;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.BusinessHoursRepository;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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
 *
 * <p><b>Working-hours away note.</b> A cross-cutting concern, not a fourth keyword family: after all
 * three of the above have had their chance to intercept — and already returned if they did —
 * {@link #withAwayNoteIfClosed} wraps whatever {@link #runWizardTurn} decided to reply with one short
 * extra line when the restaurant is currently outside its {@code Restaurant.businessHours} for today.
 * It never runs for STOP, opt-in, or story engagement, all of which return from {@link #process}
 * before the wrap call is reached, and it never blocks the wizard: closed hours only prepend a note —
 * the registration/continue/main-menu reply underneath is sent exactly as it would be during business
 * hours.
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

    // Wave 7 — in-DM ordering. Reachable ONLY from REGISTERED (via the ORDER quick-reply/persistent-menu
    // payload or an order-intent keyword), and every terminal step (checkout / cancel) returns to
    // REGISTERED with the cart cleared. Opt-out still wins over all of these: they live inside the
    // wizard switch, which process() only reaches AFTER the STOP/opt-in keyword checks.
    private static final String STATE_ORDER_BROWSING   = "ORDER_BROWSING";    // menu shown, awaiting a product pick
    private static final String STATE_ORDER_QUANTITY   = "ORDER_QUANTITY";    // product picked, awaiting a quantity
    private static final String STATE_ORDER_CONFIRMING = "ORDER_CONFIRMING";  // cart shown, awaiting checkout/add-more/cancel

    /** Ceiling on distinct lines in one in-DM cart — a conversation cart is small by nature. */
    private static final int MAX_CART_LINES = 20;
    /** Ceiling on the quantity of a single line. */
    private static final int MAX_ITEM_QTY = 99;
    /** Instagram caps quick replies at 13; leave room for the trailing Cancel button. */
    private static final int MENU_QUICK_REPLY_LIMIT = 10;
    /** Instagram quick-reply titles are limited to 20 characters. */
    private static final int QUICK_REPLY_TITLE_MAX = 20;

    // Ordering quick-reply / persistent-menu payloads. ITEM/QTY carry a dynamic suffix, so they are
    // matched by prefix in handleQuickReply rather than by an exact switch.
    private static final String PAYLOAD_ORDER      = "ORDER";
    private static final String PAYLOAD_ITEM_PREFIX = "ORDER_ITEM_";  // ORDER_ITEM_<productId>
    private static final String PAYLOAD_QTY_PREFIX  = "ORDER_QTY_";   // ORDER_QTY_<n>
    private static final String PAYLOAD_CHECKOUT   = "ORDER_CHECKOUT";
    private static final String PAYLOAD_ADD_MORE   = "ORDER_ADD_MORE";
    private static final String PAYLOAD_CANCEL     = "ORDER_CANCEL";

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
    private static final DateTimeFormatter HOURS_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Plain-text prefix for the working-hours away note (see {@link #awayNoteIfClosed}) — a
     * branding-neutral static default, deliberately: a per-restaurant configurable away text is a
     * follow-up, not in scope here (no schema change accompanies this feature).
     */
    private static final String AWAY_NOTE_CLOSED = "🕒 Biz hozir yopiqmiz.";

    private static final List<Map<String, String>> MORE_ADDRESS_QUICK_REPLIES = List.of(
            Map.of("title", "Add another", "payload", "ADD_ADDRESS"),
            Map.of("title", "Done",         "payload", "DONE")
    );

    private final InstagramBotConfigRepository configRepository;
    private final InstagramSubscriberRepository subscriberRepository;
    private final InstagramSubscriberAddressRepository addressRepository;
    private final CustomerRepository customerRepository;

    /** Read-only: the restaurant's own customer-facing hours, for {@link #awayNoteIfClosed}. This
     *  class never writes to the restaurant module. */
    private final BusinessHoursRepository businessHoursRepository;

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

    // Wave 7 in-DM ordering collaborators. productRepository/restaurantRepository are scoped by the
    // explicit restaurantId this class already threads everywhere (the webhook runs on an @Async thread
    // with no TenantContext bound of its own, so scoping never relies on the request-bound
    // restaurantFilter). orderService is the ONE canonical "create a new Order" entry point — reused so
    // an Instagram order gets the same order-number, status history, kitchen print and (@Async) owner
    // notification as every other channel. @Lazy on it keeps the bot's startup graph light and avoids
    // any construction-order coupling with the large order module.
    private final ProductRepository productRepository;
    private final RestaurantRepository restaurantRepository;
    private final OrderService orderService;

    public InstagramBotService(InstagramBotConfigRepository configRepository,
                               InstagramSubscriberRepository subscriberRepository,
                               InstagramSubscriberAddressRepository addressRepository,
                               CustomerRepository customerRepository,
                               BusinessHoursRepository businessHoursRepository,
                               InstagramApiClient apiClient,
                               RestaurantAuthorizationService restaurantAuthorizationService,
                               InstagramMessageLogger messageLogger,
                               ProductRepository productRepository,
                               RestaurantRepository restaurantRepository,
                               @Lazy OrderService orderService,
                               PlatformTransactionManager transactionManager) {
        this.configRepository = configRepository;
        this.subscriberRepository = subscriberRepository;
        this.addressRepository = addressRepository;
        this.customerRepository = customerRepository;
        this.businessHoursRepository = businessHoursRepository;
        this.apiClient = apiClient;
        this.restaurantAuthorizationService = restaurantAuthorizationService;
        this.messageLogger = messageLogger;
        this.productRepository = productRepository;
        this.restaurantRepository = restaurantRepository;
        this.orderService = orderService;
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

        // Everything from here on is the wizard proper (start, continue, re-prompt, main menu — see
        // runWizardTurn). Wrapped with the working-hours away note: closed hours prepend one line to
        // whatever the wizard was already going to send, never instead of it, and never for any of the
        // three replies above this point (blocked / story engagement / opt-out / opt-in), which have
        // all already returned by now — see the class javadoc's "Working-hours away note" paragraph.
        return withAwayNoteIfClosed(restaurantId,
                runWizardTurn(config, senderIgsid, username, kind, text, quickReplyPayload,
                        existing, explicitOptIn));
    }

    /**
     * The registration wizard proper: (re-)start it for a new sender or a restart keyword, otherwise
     * dispatch the inbound event against the subscriber's current conversation state. Split out of
     * {@link #process} purely so the away-note wrap has exactly one call site — this method's contract
     * is otherwise unchanged from before that feature existed.
     */
    private PendingReply runWizardTurn(InstagramBotConfig config, String senderIgsid, String username,
                                       InstagramInboundKind kind, String text, String quickReplyPayload,
                                       Optional<InstagramSubscriber> existing, boolean explicitOptIn) {
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
            case STATE_ORDER_BROWSING          -> handleBrowsingText(subscriber, text);
            case STATE_ORDER_QUANTITY          -> handleQuantityText(subscriber, text);
            case STATE_ORDER_CONFIRMING        -> handleConfirmingText(subscriber, text);
            case STATE_REGISTERED              -> handleRegisteredText(subscriber, text);
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
        // Ordering states re-render their whole screen (the menu, or the cart) rather than a one-liner,
        // so a story reply / unsupported attachment / stale postback mid-order shows the customer where
        // they are instead of a bare sentence.
        if (STATE_ORDER_BROWSING.equals(state))   return reBrowse(subscriber);
        if (STATE_ORDER_QUANTITY.equals(state))   return rePromptQuantity(subscriber);
        if (STATE_ORDER_CONFIRMING.equals(state)) return confirmReply(subscriber, null);
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
    // Working-hours away message — a customer who DMs outside business hours used to be pushed
    // straight into the registration wizard with no indication the restaurant was closed. This wraps
    // (not intercepts) the wizard's own reply; see the class javadoc and runWizardTurn's call site.
    // -------------------------------------------------------------------------

    /**
     * Prepend a short "we're closed" note to {@code reply} when {@code restaurantId} is currently
     * outside its business hours; otherwise return it unchanged. {@code reply} is never inspected
     * beyond that — the wizard already decided what to send, and this only ever adds one line ahead
     * of it, so a closed restaurant still lets the visitor register or continue exactly as before.
     */
    private PendingReply withAwayNoteIfClosed(Long restaurantId, PendingReply reply) {
        if (reply == null) {
            return null;
        }
        String awayNote = awayNoteIfClosed(restaurantId);
        if (awayNote == null) {
            return reply;
        }
        String text = awayNote + "\n\n" + reply.text();
        return reply.quickReplies() != null
                ? PendingReply.withQuickReplies(reply.igsid(), text, reply.quickReplies())
                : PendingReply.text(reply.igsid(), text);
    }

    /**
     * Best-effort "is this restaurant closed right now" check against {@code Restaurant.businessHours}
     * for the current day-of-week and local time. No "is open now" helper already existed in the app
     * (checked {@code AvailabilityService} and {@code ShiftTimeService}, the two other consumers of
     * {@link BusinessHoursRepository}); this mirrors {@code ShiftTimeService#getCurrentBusinessDay}'s
     * yesterday-then-today overnight-crossing pattern against the same rows, including its implicit
     * system-default-zone clock ({@code LocalDate.now()} / {@code LocalTime.now()} — {@code Restaurant}
     * has no timezone column, see {@code POSDashboardAssembler}'s identical note) and its
     * {@code closeTime.isBefore(openTime) || closeTime.equals(openTime)} crosses-midnight test, so this
     * and the financial module's shift math agree on what "open" means for the same data.
     *
     * <p>Returns {@code null} — say nothing, behave exactly as before this feature existed — unless the
     * answer is a CONFIDENT "closed". Open hours, no row for today at all, and a day explicitly flagged
     * {@code closed=true} all return {@code null} rather than a note. The explicitly-closed case reads
     * oddly at first — surely that is the clearest "closed" signal there is — but an ordinary
     * outside-hours gap hands this method a same-day opening time to quote (the 02:00-DM scenario this
     * feature exists for), while a day off does not: today's own row cannot say when service resumes,
     * and reliably walking further than one day ahead is more machinery than a "best-effort, if
     * derivable" note calls for. Rather than print a closed note with no useful information in it, this
     * stays silent — the same "uncertain ⇒ do not block" rule the missing-hours case uses.
     *
     * @return a short Uzbek away note (with a next-opening time when one is cheaply derivable), or
     *         {@code null} when open, unknown, or any lookup failed
     */
    private String awayNoteIfClosed(Long restaurantId) {
        if (restaurantId == null || businessHoursRepository == null) {
            return null;
        }
        try {
            LocalDate today = LocalDate.now();
            LocalTime now = LocalTime.now();
            DayOfWeek todayDow = today.getDayOfWeek();

            // Still inside YESTERDAY's overnight shift (e.g. opens 22:00, closes 02:00)? Checked
            // before today's own row, same ordering ShiftTimeService#getCurrentBusinessDay uses and
            // for the same reason: a 01:00 message on Tuesday is answered by Monday night's hours,
            // not by whatever Tuesday's row (which may not even cover the early morning) says.
            Optional<BusinessHours> yesterday = businessHoursRepository
                    .findByRestaurant_IdAndDayOfWeek(restaurantId, todayDow.minus(1));
            if (stillOpenFromYesterday(yesterday, now)) {
                return null;
            }

            Optional<BusinessHours> todayHours = businessHoursRepository
                    .findByRestaurant_IdAndDayOfWeek(restaurantId, todayDow);
            if (todayHours.isEmpty() || Boolean.TRUE.equals(todayHours.get().getClosed())) {
                return null; // no data, or an explicit day off — not a confident "closed", see javadoc
            }

            BusinessHours hours = todayHours.get();
            LocalTime open = hours.getOpenTime();
            LocalTime close = hours.getCloseTime();
            if (open == null || close == null) {
                return null;
            }

            boolean crossesMidnight = crossesMidnight(open, close);
            boolean openNow = crossesMidnight
                    ? !now.isBefore(open)
                    : (!now.isBefore(open) && now.isBefore(close));
            if (openNow) {
                return null;
            }

            return buildAwayNote(restaurantId, todayDow, now, open, crossesMidnight);
        } catch (Exception e) {
            // Best-effort, by design (class javadoc + the task this shipped under): a business-hours
            // lookup that fails must never turn into a broken webhook reply — behave exactly as if
            // hours were unknown.
            log.warn("Could not evaluate business hours for restaurant {} — sending the wizard's reply "
                    + "without an away note", restaurantId, e);
            return null;
        }
    }

    /**
     * Still inside yesterday's shift? Only true when yesterday had hours, was not a day off, crossed
     * midnight, and {@code now} has not yet reached its close time.
     */
    private boolean stillOpenFromYesterday(Optional<BusinessHours> yesterdayHours, LocalTime now) {
        if (yesterdayHours.isEmpty() || Boolean.TRUE.equals(yesterdayHours.get().getClosed())) {
            return false;
        }
        LocalTime open = yesterdayHours.get().getOpenTime();
        LocalTime close = yesterdayHours.get().getCloseTime();
        if (open == null || close == null) {
            return false;
        }
        return crossesMidnight(open, close) && now.isBefore(close);
    }

    /**
     * {@code closeTime <= openTime} reads as a shift that runs past midnight (or, for equal times, a
     * full 24h day) — the same test {@code AvailabilityService}/{@code ShiftTimeService} already apply
     * to these rows, kept in one place here since this class checks it for two different days.
     */
    private static boolean crossesMidnight(LocalTime openTime, LocalTime closeTime) {
        return closeTime.isBefore(openTime) || closeTime.equals(openTime);
    }

    /** Compose the away note, quoting a next-opening time when one is cheaply derivable (see
     *  {@link #awayNoteIfClosed}'s javadoc for what "cheaply" excludes). */
    private String buildAwayNote(Long restaurantId, DayOfWeek todayDow, LocalTime now, LocalTime open,
                                 boolean crossesMidnight) {
        String whenClause = null;
        if (now.isBefore(open)) {
            // Opens later today — the scenario the roadmap named: a 02:00 DM, hours starting at 09:00.
            whenClause = "Bugun soat " + open.format(HOURS_FMT) + " da ochamiz.";
        } else if (!crossesMidnight) {
            // Past closing on an ordinary same-day window: a one-day-ahead, best-effort look at
            // tomorrow only (no unbounded forward search) — matches the "if derivable" ask. Unreached
            // when crossesMidnight: that combination is always "open now" and already returned above.
            Optional<BusinessHours> tomorrow = businessHoursRepository
                    .findByRestaurant_IdAndDayOfWeek(restaurantId, todayDow.plus(1));
            if (tomorrow.isPresent() && !Boolean.TRUE.equals(tomorrow.get().getClosed())
                    && tomorrow.get().getOpenTime() != null) {
                whenClause = "Ertaga soat " + tomorrow.get().getOpenTime().format(HOURS_FMT) + " da ochamiz.";
            }
        }
        return whenClause != null ? AWAY_NOTE_CLOSED + " " + whenClause : AWAY_NOTE_CLOSED;
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
        // A full (re)start abandons any in-progress in-DM order: restarting the wizard is a clean-slate
        // action, so a half-built cart must not survive into the fresh registration.
        subscriber.setOrderCart(null);
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
        // --- Wave 7 in-DM ordering payloads. Each is gated on the ordering state it belongs to, exactly
        // like the ADD_ADDRESS/DONE gating below: an Instagram postback bubble stays tappable forever, so
        // a stale "Checkout" or "1" must not act on a conversation that has moved on. A payload that
        // arrives in the wrong state re-prompts for the current state instead of acting. ---
        if (PAYLOAD_CANCEL.equals(payload)) {
            // Cancel is honoured from any ordering state (it only ever aborts). Outside ordering there is
            // nothing to cancel, so fall back to the main menu.
            return inOrderFlow(state) ? cancelOrder(subscriber) : sendMainMenu(subscriber);
        }
        if (PAYLOAD_ORDER.equals(payload)) {
            // Entry point — only from REGISTERED, mirroring the order-keyword entry in handleRegisteredText.
            return STATE_REGISTERED.equals(state) ? startOrder(subscriber) : rePromptForState(subscriber, state);
        }
        if (payload.startsWith(PAYLOAD_ITEM_PREFIX)) {
            return STATE_ORDER_BROWSING.equals(state)
                    ? handleItemPayload(subscriber, payload)
                    : rePromptForState(subscriber, state);
        }
        if (payload.startsWith(PAYLOAD_QTY_PREFIX)) {
            if (!STATE_ORDER_QUANTITY.equals(state)) {
                return rePromptForState(subscriber, state);
            }
            Integer qty = parsePositiveInt(payload.substring(PAYLOAD_QTY_PREFIX.length()));
            return (qty == null || qty > MAX_ITEM_QTY)
                    ? rePromptForState(subscriber, state)
                    : applyQuantity(subscriber, qty);
        }
        if (PAYLOAD_CHECKOUT.equals(payload)) {
            return STATE_ORDER_CONFIRMING.equals(state)
                    ? checkout(subscriber)
                    : rePromptForState(subscriber, state);
        }
        if (PAYLOAD_ADD_MORE.equals(payload)) {
            return STATE_ORDER_CONFIRMING.equals(state)
                    ? resumeBrowsing(subscriber)
                    : rePromptForState(subscriber, state);
        }

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
        //
        // Linking-bug fix: matched on CANONICAL phone (see findCustomerByPhone /
        // canonicalizePhoneForMatching), not an exact string match — an exact match missed a locally
        // formatted "901234567" or spaced "+998 90 123 45 67" against a customer stored as
        // "+998901234567", which is exactly what a real customer types differently across two channels.
        if (subscriber.getPhone() != null && subscriber.getCustomer() == null) {
            findCustomerByPhone(subscriber.getPhone(), subscriber.getRestaurantId())
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
        return PendingReply.withQuickReplies(subscriber.getIgsid(),
                "👋 Salom, " + subscriber.getDisplayNameOrFallback() + "!\n\n" +
                "🛍 Buyurtma berish uchun \"buyurtma\" deb yozing yoki tugmani bosing.\n" +
                "Ma'lumotlarni yangilash uchun \"start\" yozing.",
                List.of(Map.of("title", "🛍 Buyurtma berish", "payload", PAYLOAD_ORDER)));
    }

    // =========================================================================
    // Wave 7 — in-DM ordering.
    //
    // A REGISTERED subscriber can order directly in the DM. Entry is either the ORDER quick-reply/
    // persistent-menu payload (handleQuickReply) or an order-intent keyword typed while REGISTERED
    // (handleRegisteredText). From there the flow is a three-state loop carried on conversationState,
    // with the in-progress cart held on instagram_subscribers.order_cart (JSONB, V181) — the lightest
    // possible store, no parallel cart-entity system:
    //
    //   ORDER_BROWSING   — menu listed; pick a product (a number, or an ORDER_ITEM_<id> button)
    //   ORDER_QUANTITY   — product picked (added to the cart at qty 1); send a quantity to finalise it
    //   ORDER_CONFIRMING — cart summarised; Checkout / Add more / Cancel
    //
    // Checkout assembles a real Order (orderSource = INSTAGRAM_BOT) from the cart and hands it to the ONE
    // canonical creation path, OrderService.createOrder(Order) — the same entry every other channel uses
    // — then clears the cart and returns to REGISTERED. Cancel (the ORDER_CANCEL button, or a "/cancel"-
    // family keyword that is deliberately NOT one of isOptOutKeyword's words) does the same minus the
    // order. The whole flow lives after process()'s opt-out/opt-in checks, so STOP still always wins.
    //
    // TenantContext note: like the rest of this class, everything below scopes explicitly to the
    // subscriber's restaurantId (productRepository/restaurantRepository take it as an argument); the
    // webhook's processEntry already binds TenantContext around the dispatch, but this code never relies
    // on it being bound.
    // =========================================================================

    /** Text arriving while REGISTERED: an order-intent keyword starts the ordering flow, else main menu. */
    private PendingReply handleRegisteredText(InstagramSubscriber subscriber, String text) {
        if (isOrderKeyword(text)) {
            return startOrder(subscriber);
        }
        return sendMainMenu(subscriber);
    }

    /**
     * Enter the ordering flow: list the tenant's active (LIVE) menu and move to ORDER_BROWSING with an
     * empty cart. Only ever called for a REGISTERED subscriber (the two entry points both gate on it).
     * A restaurant with no live products is told so and left in REGISTERED — nothing to order.
     */
    private PendingReply startOrder(InstagramSubscriber subscriber) {
        List<Product> menu = activeMenu(subscriber.getRestaurantId());
        if (menu.isEmpty()) {
            return PendingReply.text(subscriber.getIgsid(),
                    "Kechirasiz, hozircha menyu mavjud emas. Keyinroq urinib ko'ring.");
        }
        subscriber.setOrderCart(new ArrayList<>());
        subscriber.setConversationState(STATE_ORDER_BROWSING);
        subscriber.touch();
        subscriberRepository.save(subscriber);
        return menuReply(subscriber, menu);
    }

    /** ORDER_BROWSING + typed text: a menu number picks that product; "/cancel" aborts; else re-list. */
    private PendingReply handleBrowsingText(InstagramSubscriber subscriber, String text) {
        if (isOrderCancelKeyword(text)) {
            return cancelOrder(subscriber);
        }
        List<Product> menu = activeMenu(subscriber.getRestaurantId());
        if (menu.isEmpty()) {
            return abortNoMenu(subscriber);
        }
        Integer idx = parsePositiveInt(text);
        if (idx == null || idx > menu.size()) {
            return PendingReply.withQuickReplies(subscriber.getIgsid(),
                    "Iltimos, ro'yxatdan mahsulot raqamini tanlang:", menuQuickReplies(menu));
        }
        return selectProduct(subscriber, menu.get(idx - 1));
    }

    /** ORDER_BROWSING + ORDER_ITEM_<id> button: pick that product if it is still on the live menu. */
    private PendingReply handleItemPayload(InstagramSubscriber subscriber, String payload) {
        Long productId = parseIdSuffix(payload, PAYLOAD_ITEM_PREFIX);
        List<Product> menu = activeMenu(subscriber.getRestaurantId());
        Product picked = menu.stream()
                .filter(p -> p.getId() != null && p.getId().equals(productId))
                .findFirst()
                .orElse(null);
        if (picked == null) {
            // A stale/unknown button (menu changed since it was shown) — just re-list.
            return menu.isEmpty() ? abortNoMenu(subscriber) : menuReply(subscriber, menu);
        }
        return selectProduct(subscriber, picked);
    }

    /**
     * Add the picked product to the cart at a provisional quantity of 1 and move to ORDER_QUANTITY, where
     * the next number finalises that line's quantity. Keeping the pending item AS the last cart line (not
     * a separate scalar column) is what lets the cart be the single piece of ordering state — the line's
     * quantity is always overwritten by a valid quantity before ORDER_CONFIRMING is ever shown.
     */
    private PendingReply selectProduct(InstagramSubscriber subscriber, Product product) {
        List<InstagramCartLine> cart = mutableCart(subscriber);
        if (cart.size() >= MAX_CART_LINES) {
            subscriber.setConversationState(STATE_ORDER_CONFIRMING);
            subscriberRepository.save(subscriber);
            return confirmReply(subscriber,
                    "Savatda juda ko'p tur bor. Buyurtmani tasdiqlang yoki bekor qiling.");
        }
        cart.add(InstagramCartLine.builder()
                .productId(product.getId())
                .productName(product.getName())
                .unitPrice(product.getPrice())
                .quantity(1)
                .build());
        subscriber.setOrderCart(cart);
        subscriber.setConversationState(STATE_ORDER_QUANTITY);
        subscriberRepository.save(subscriber);

        List<Map<String, String>> qrs = new ArrayList<>();
        qrs.add(Map.of("title", "1", "payload", PAYLOAD_QTY_PREFIX + "1"));
        qrs.add(Map.of("title", "2", "payload", PAYLOAD_QTY_PREFIX + "2"));
        qrs.add(Map.of("title", "3", "payload", PAYLOAD_QTY_PREFIX + "3"));
        qrs.add(cancelQuickReply());
        return PendingReply.withQuickReplies(subscriber.getIgsid(),
                "\"" + product.getName() + "\" tanlandi.\nNechta kerak? Sonini yuboring (1–"
                        + MAX_ITEM_QTY + "):", qrs);
    }

    /** ORDER_QUANTITY + typed text: a valid number finalises the pending line; "/cancel" aborts. */
    private PendingReply handleQuantityText(InstagramSubscriber subscriber, String text) {
        if (isOrderCancelKeyword(text)) {
            return cancelOrder(subscriber);
        }
        Integer qty = parsePositiveInt(text);
        if (qty == null || qty > MAX_ITEM_QTY) {
            return PendingReply.text(subscriber.getIgsid(),
                    "Iltimos, 1 dan " + MAX_ITEM_QTY + " gacha son yuboring:");
        }
        return applyQuantity(subscriber, qty);
    }

    /** Set the pending (last) cart line's quantity and advance to ORDER_CONFIRMING. */
    private PendingReply applyQuantity(InstagramSubscriber subscriber, int qty) {
        List<InstagramCartLine> cart = mutableCart(subscriber);
        if (cart.isEmpty()) {
            // Defensive: no pending line (a stale quantity button) — send them back to the menu.
            return reBrowseFrom(subscriber);
        }
        cart.get(cart.size() - 1).setQuantity(qty);
        subscriber.setOrderCart(cart);
        subscriber.setConversationState(STATE_ORDER_CONFIRMING);
        subscriberRepository.save(subscriber);
        return confirmReply(subscriber, null);
    }

    /** ORDER_CONFIRMING + typed text: yes/checkout, add-more, or "/cancel"; else re-show the cart. */
    private PendingReply handleConfirmingText(InstagramSubscriber subscriber, String text) {
        if (isOrderCancelKeyword(text)) {
            return cancelOrder(subscriber);
        }
        String t = text == null ? "" : text.trim().toLowerCase();
        if (t.equals("ha") || t.equals("yes") || t.equals("ok") || t.equals("checkout")
                || t.equals("tasdiq") || t.equals("tasdiqlash") || t.equals("+1")) {
            return checkout(subscriber);
        }
        if (t.equals("yana") || t.equals("add") || t.equals("more") || t.equals("+")) {
            return resumeBrowsing(subscriber);
        }
        return confirmReply(subscriber,
                "Tushunmadim. Tasdiqlash, yana qo'shish yoki bekor qilishni tanlang.");
    }

    /** Go back to ORDER_BROWSING KEEPING the cart (the "Add more" path), re-listing the menu. */
    private PendingReply resumeBrowsing(InstagramSubscriber subscriber) {
        List<Product> menu = activeMenu(subscriber.getRestaurantId());
        if (menu.isEmpty()) {
            // Nothing left to add — just re-show what they already have.
            return confirmReply(subscriber, "Menyuda boshqa mahsulot yo'q.");
        }
        subscriber.setConversationState(STATE_ORDER_BROWSING);
        subscriberRepository.save(subscriber);
        return menuReply(subscriber, menu);
    }

    /**
     * Turn the cart into a real Order and persist it through {@link OrderService#createOrder(Order)} —
     * the one canonical new-order entry point (order number, NEW status + history, kitchen print, @Async
     * owner notification). Order type is DELIVERY to the subscriber's default saved address when they
     * have one, else TAKEAWAY. The order links to the subscriber's Customer when set, otherwise it is a
     * guest order (Order.customer is nullable — the same guest path SelfServiceOrderService takes when it
     * has no customer). Totals are the trivial happy-path sum (no tax/coupon in a DM order), mirroring
     * SelfServiceOrderService's minimal createOrder. Cart cleared and state reset to REGISTERED after.
     */
    private PendingReply checkout(InstagramSubscriber subscriber) {
        List<InstagramCartLine> cart = subscriber.getOrderCart();
        if (cart == null || cart.isEmpty()) {
            return reBrowseFrom(subscriber);
        }

        Long restaurantId = subscriber.getRestaurantId();
        Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
        if (restaurant == null) {
            log.warn("Instagram in-DM checkout for subscriber {} aborted — restaurant {} not found",
                    subscriber.getIgsid(), restaurantId);
            return finishOrderConversation(subscriber,
                    "Kechirasiz, buyurtmani rasmiylashtira olmadik. Keyinroq urinib ko'ring.");
        }

        List<InstagramSubscriberAddress> addresses = addressRepository.findAllBySubscriber(subscriber);
        InstagramSubscriberAddress deliveryAddress = pickDefaultAddress(addresses);
        OrderType orderType = deliveryAddress != null ? OrderType.DELIVERY : OrderType.TAKEAWAY;

        Order order = Order.builder()
                .restaurant(restaurant)
                .customer(subscriber.getCustomer())     // may be null → guest order
                .orderType(orderType)
                .orderSource(OrderSource.INSTAGRAM_BOT)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .customerNotes(buildOrderNotes(subscriber, deliveryAddress))
                .placedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();
        for (InstagramCartLine line : cart) {
            BigDecimal lineTotal = line.lineTotal();
            subtotal = subtotal.add(lineTotal);
            items.add(OrderItem.builder()
                    .order(order)
                    .productId(line.getProductId())
                    .productName(line.getProductName())
                    .quantity(line.getQuantity())
                    .unitPrice(line.getUnitPrice())
                    .totalPrice(lineTotal)
                    .isBundle(false)
                    .build());
        }
        order.setSubtotal(subtotal);
        order.setTotal(subtotal);
        order.setItems(items);

        Order saved = orderService.createOrder(order);

        // Clear the cart and hand the subscriber back to REGISTERED in the same committed unit of work.
        subscriber.setOrderCart(null);
        subscriber.setConversationState(STATE_REGISTERED);
        subscriber.touch();
        subscriberRepository.save(subscriber);

        BigDecimal total = saved.getTotal() != null ? saved.getTotal() : subtotal;
        StringBuilder sb = new StringBuilder();
        sb.append("✅ Buyurtmangiz qabul qilindi!\n\n");
        sb.append("№ ").append(saved.getOrderNumber()).append("\n");
        sb.append("Turi: ").append(orderType == OrderType.DELIVERY ? "Yetkazib berish" : "Olib ketish").append("\n");
        if (deliveryAddress != null) {
            sb.append("Manzil: ").append(deliveryAddress.getAddress()).append("\n");
        }
        sb.append("Jami: ").append(formatPrice(total)).append("\n\n");
        sb.append("Rahmat! Tez orada siz bilan bog'lanamiz.");
        return PendingReply.text(subscriber.getIgsid(), sb.toString());
    }

    /** Abort an in-progress order: clear the cart, return to REGISTERED, confirm. */
    private PendingReply cancelOrder(InstagramSubscriber subscriber) {
        return finishOrderConversation(subscriber,
                "Buyurtma bekor qilindi. Yana kerak bo'lsa \"buyurtma\" deb yozing.");
    }

    /** Clear cart + reset to REGISTERED + save, returning {@code message} as the reply. */
    private PendingReply finishOrderConversation(InstagramSubscriber subscriber, String message) {
        subscriber.setOrderCart(null);
        subscriber.setConversationState(STATE_REGISTERED);
        subscriber.touch();
        subscriberRepository.save(subscriber);
        return PendingReply.text(subscriber.getIgsid(), message);
    }

    /** No live menu to order from: clear any cart and drop back to REGISTERED. */
    private PendingReply abortNoMenu(InstagramSubscriber subscriber) {
        return finishOrderConversation(subscriber, "Kechirasiz, hozircha menyu mavjud emas.");
    }

    // ---- ordering render/helpers ----

    private List<Product> activeMenu(Long restaurantId) {
        return productRepository.findByRestaurant_IdAndStatus(restaurantId, ProductStatus.LIVE);
    }

    /** The menu screen: a numbered text list plus tappable quick replies and a Cancel button. */
    private PendingReply menuReply(InstagramSubscriber subscriber, List<Product> menu) {
        StringBuilder sb = new StringBuilder("🍽 Menyu — mahsulot raqamini yuboring yoki tugmani bosing:\n\n");
        int i = 1;
        for (Product p : menu) {
            sb.append(i++).append(". ").append(p.getName())
                    .append(" — ").append(formatPrice(p.getPrice())).append("\n");
        }
        sb.append("\nBekor qilish uchun tugma yoki /cancel.");
        return PendingReply.withQuickReplies(subscriber.getIgsid(), sb.toString(), menuQuickReplies(menu));
    }

    /** Re-list the menu for a subscriber already in ORDER_BROWSING (no state change). */
    private PendingReply reBrowse(InstagramSubscriber subscriber) {
        List<Product> menu = activeMenu(subscriber.getRestaurantId());
        return menu.isEmpty() ? abortNoMenu(subscriber) : menuReply(subscriber, menu);
    }

    /** Reset to the menu (ORDER_BROWSING) from an inconsistent ordering state, preserving the cart. */
    private PendingReply reBrowseFrom(InstagramSubscriber subscriber) {
        List<Product> menu = activeMenu(subscriber.getRestaurantId());
        if (menu.isEmpty()) {
            return abortNoMenu(subscriber);
        }
        subscriber.setConversationState(STATE_ORDER_BROWSING);
        subscriberRepository.save(subscriber);
        return menuReply(subscriber, menu);
    }

    private PendingReply rePromptQuantity(InstagramSubscriber subscriber) {
        List<InstagramCartLine> cart = subscriber.getOrderCart();
        String name = (cart != null && !cart.isEmpty())
                ? cart.get(cart.size() - 1).getProductName() : "mahsulot";
        return PendingReply.text(subscriber.getIgsid(),
                "\"" + name + "\" uchun nechta kerak? Sonini yuboring (1–" + MAX_ITEM_QTY + "):");
    }

    /** The cart screen: line-by-line summary, grand total, and Checkout / Add more / Cancel buttons. */
    private PendingReply confirmReply(InstagramSubscriber subscriber, String prefix) {
        List<InstagramCartLine> cart = subscriber.getOrderCart();
        if (cart == null || cart.isEmpty()) {
            return PendingReply.text(subscriber.getIgsid(),
                    "Savatingiz bo'sh. Buyurtma berish uchun \"buyurtma\" deb yozing.");
        }
        StringBuilder sb = new StringBuilder();
        if (prefix != null) {
            sb.append(prefix).append("\n\n");
        }
        sb.append("🧾 Savatingiz:\n");
        BigDecimal total = BigDecimal.ZERO;
        for (InstagramCartLine line : cart) {
            BigDecimal lt = line.lineTotal();
            total = total.add(lt);
            sb.append("• ").append(line.getProductName()).append(" x").append(line.getQuantity())
                    .append(" = ").append(formatPrice(lt)).append("\n");
        }
        sb.append("\nJami: ").append(formatPrice(total)).append("\n\nTasdiqlaysizmi?");
        return PendingReply.withQuickReplies(subscriber.getIgsid(), sb.toString(), List.of(
                Map.of("title", "✅ Tasdiqlash", "payload", PAYLOAD_CHECKOUT),
                Map.of("title", "➕ Yana qo'shish", "payload", PAYLOAD_ADD_MORE),
                Map.of("title", "❌ Bekor qilish", "payload", PAYLOAD_CANCEL)));
    }

    /** Product quick replies (capped) followed by a Cancel button. */
    private List<Map<String, String>> menuQuickReplies(List<Product> menu) {
        List<Map<String, String>> qrs = new ArrayList<>();
        menu.stream().limit(MENU_QUICK_REPLY_LIMIT).forEach(p ->
                qrs.add(Map.of("title", truncateTitle(p.getName()),
                        "payload", PAYLOAD_ITEM_PREFIX + p.getId())));
        qrs.add(cancelQuickReply());
        return qrs;
    }

    private static Map<String, String> cancelQuickReply() {
        return Map.of("title", "❌ Bekor qilish", "payload", PAYLOAD_CANCEL);
    }

    /** A mutable copy of the current cart (never the persisted list instance itself). */
    private List<InstagramCartLine> mutableCart(InstagramSubscriber subscriber) {
        return subscriber.getOrderCart() == null
                ? new ArrayList<>() : new ArrayList<>(subscriber.getOrderCart());
    }

    private InstagramSubscriberAddress pickDefaultAddress(List<InstagramSubscriberAddress> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        return addresses.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsDefault()))
                .findFirst()
                .orElse(addresses.get(0));
    }

    private String buildOrderNotes(InstagramSubscriber subscriber, InstagramSubscriberAddress address) {
        StringBuilder sb = new StringBuilder("Instagram DM buyurtma");
        if (subscriber.getPhone() != null && !subscriber.getPhone().isBlank()) {
            sb.append(" • tel: ").append(subscriber.getPhone());
        }
        if (address != null) {
            sb.append(" • manzil: ").append(address.getAddress());
        }
        return sb.toString();
    }

    /** Prices are whole-som UZS amounts; render without trailing decimal noise. */
    private static String formatPrice(BigDecimal price) {
        if (price == null) {
            return "0 so'm";
        }
        return price.stripTrailingZeros().toPlainString() + " so'm";
    }

    private static String truncateTitle(String name) {
        if (name == null) {
            return "";
        }
        return name.length() <= QUICK_REPLY_TITLE_MAX
                ? name : name.substring(0, QUICK_REPLY_TITLE_MAX - 1) + "…";
    }

    private static boolean inOrderFlow(String state) {
        return STATE_ORDER_BROWSING.equals(state)
                || STATE_ORDER_QUANTITY.equals(state)
                || STATE_ORDER_CONFIRMING.equals(state);
    }

    /**
     * Order-intent keywords that start the flow from REGISTERED. Exact match like the other keyword
     * predicates in this class, and deliberately disjoint from every opt-out/opt-in/restart word so none
     * of them is shadowed.
     */
    private boolean isOrderKeyword(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.equals("order") || t.equals("buyurtma") || t.equals("menu")
                || t.equals("menyu") || t.equals("zakaz");
    }

    /**
     * In-flow cancel keywords. Slash-prefixed / "orqaga" ON PURPOSE: plain "cancel"/"bekor" are
     * {@link #isOptOutKeyword} words that process() intercepts BEFORE any wizard dispatch (opt-out always
     * wins — a mandatory invariant), so they could never reach an ordering step anyway. The tappable
     * ORDER_CANCEL button is the primary cancel affordance; this is the typed escape hatch.
     */
    private boolean isOrderCancelKeyword(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.equals("/cancel") || t.equals("/bekor") || t.equals("orqaga");
    }

    private static Integer parsePositiveInt(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            long v = Long.parseLong(t);
            return (v < 1 || v > Integer.MAX_VALUE) ? null : (int) v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseIdSuffix(String payload, String prefix) {
        try {
            return Long.parseLong(payload.substring(prefix.length()).trim());
        } catch (RuntimeException e) {
            return null;
        }
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
     * Manual escape hatch for when the wizard's phone-based auto-link (see {@link
     * #completeRegistration}) did not fire — a phone typo, a customer created after the subscriber
     * registered, or simply a format {@link #canonicalizePhoneForMatching} could not resolve.
     * Tenant-scoped on BOTH sides: the subscriber id goes through {@link #findSubscriberForCallerOrThrow}
     * exactly like every other admin action here, and the customer id is additionally required to
     * belong to that SAME restaurant — the subscriber's, not merely the caller's, so this also works
     * correctly for a cross-tenant SUPER_ADMIN caller — so neither a foreign subscriber id nor a
     * foreign customer id can bridge two tenants' data. A customer id that does not exist, or belongs
     * to a different restaurant, is rejected as not-found rather than confirming its own existence —
     * the same "a guessed id reads as not-found" philosophy this class already applies to subscriber
     * ids (see the class-level comment above {@link #blockSubscriber}).
     *
     * @throws BadRequestException if {@code customerId} is null
     * @throws ResourceNotFoundException if the subscriber id is unknown/foreign, or the customer id is
     *         unknown/foreign to the subscriber's restaurant
     */
    @Transactional
    public InstagramSubscriber linkSubscriberToCustomer(Long id, Long customerId) {
        InstagramSubscriber subscriber = findSubscriberForCallerOrThrow(id);
        if (customerId == null) {
            throw new BadRequestException("customerId is required");
        }
        Customer customer = customerRepository.findById(customerId)
                .filter(c -> c.getRestaurantId() != null
                        && c.getRestaurantId().equals(subscriber.getRestaurantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));
        subscriber.setCustomer(customer);
        InstagramSubscriber saved = subscriberRepository.save(subscriber);
        log.info("Instagram subscriber {} manually linked to customer {} (restaurant {})",
                id, customerId, subscriber.getRestaurantId());
        return saved;
    }

    /**
     * Clear a subscriber's customer link — the undo for {@link #linkSubscriberToCustomer}, or for an
     * auto-link that {@link #completeRegistration} got wrong (e.g. two customers sharing a canonical
     * phone — see {@link #findCustomerByPhone}'s javadoc). Tenant-scoped like every other admin action
     * here. A no-op save (not an error) when the subscriber had no customer linked already.
     */
    @Transactional
    public InstagramSubscriber unlinkSubscriberFromCustomer(Long id) {
        InstagramSubscriber subscriber = findSubscriberForCallerOrThrow(id);
        subscriber.setCustomer(null);
        InstagramSubscriber saved = subscriberRepository.save(subscriber);
        log.info("Instagram subscriber {} unlinked from its customer (restaurant {})",
                id, subscriber.getRestaurantId());
        return saved;
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

    // -------------------------------------------------------------------------
    // Subscriber → customer phone matching. Separate from normalizePhone (above), which is what the
    // wizard still uses to STORE subscriber.phone and to render it back in the completeRegistration
    // summary — unchanged by this. Everything below exists solely to compare the subscriber's phone
    // against Customer.phone for auto-linking, tolerant of the two sides being formatted differently.
    // -------------------------------------------------------------------------

    /**
     * Resolve {@code subscriberPhone} to an existing customer of {@code restaurantId}, tolerant of
     * whatever format {@code Customer.phone} happens to be stored in. There is no single canonical form
     * already enforced when a customer is created: {@code ReservationService#normalizePhone} only
     * strips whitespace/dashes/parens (no country-code handling), {@code
     * ConsumerAuthService#normalizePhoneNumber} strips everything but digits and "+", and both {@code
     * SelfServiceOrderService#findOrCreateCustomer} and {@code CustomerService#createCustomer} persist
     * the caller's raw input verbatim — so a customer created via SMS/order/reservation/self-service can
     * be sitting in the table as "+998901234567", "998901234567", "901234567", or "0901234567",
     * depending on which flow and what the customer originally typed. {@link
     * #canonicalizePhoneForMatching} defines ONE form to compare on; this method applies it to both
     * sides rather than trusting either one to already be in it.
     *
     * <p>Tries an exact match on the canonical form first — cheap, and already correct for every
     * customer whose phone happens to already be stored that way (increasingly the common case, since
     * every wizard prompt in this file shows "+998901234567" as the example). Only when that misses does
     * it fall back to a tenant-scoped {@code LIKE} search on the bare 9-digit national number — a fast
     * candidate pre-filter, never itself the match, since {@code Customer.phone} may carry punctuation
     * the exact-match branch would miss — and re-canonicalizes every candidate it returns, keeping only
     * an exact canonical match.
     *
     * <p>If more than one of the tenant's customers canonicalize to the SAME phone (a pre-existing
     * data-quality issue this method did not create and has no principled way to resolve by itself —
     * which one is "right"?), it links deterministically to the lowest-id row — the same oldest-row
     * tie-break {@link CustomerRepository#findFirstByPhoneOrderByIdAsc} already uses elsewhere — and
     * logs a warning; an admin can repoint the link via {@link #linkSubscriberToCustomer} /
     * {@link #unlinkSubscriberFromCustomer} once they know which customer is correct.
     *
     * @return the matching customer, or empty when none of the tenant's customers canonicalize to the
     *         same phone
     */
    private Optional<Customer> findCustomerByPhone(String subscriberPhone, Long restaurantId) {
        String canonical = canonicalizePhoneForMatching(subscriberPhone);
        if (canonical.isBlank()) {
            return Optional.empty();
        }

        Optional<Customer> exact = customerRepository.findByPhoneAndRestaurantId(canonical, restaurantId);
        if (exact.isPresent()) {
            return exact;
        }

        String digits = canonical.replaceAll("[^\\d]", "");
        String searchDigits = digits.length() > 9 ? digits.substring(digits.length() - 9) : digits;
        if (searchDigits.isEmpty()) {
            return Optional.empty();
        }

        List<Customer> matches = customerRepository
                .findByPhoneContainingAndRestaurantId(searchDigits, restaurantId).stream()
                .filter(c -> canonical.equals(canonicalizePhoneForMatching(c.getPhone())))
                .sorted(Comparator.comparing(Customer::getId))
                .toList();

        if (matches.size() > 1) {
            log.warn("Instagram phone-link: {} customers in restaurant {} share a normalized phone — "
                            + "linking subscriber to the lowest id ({})",
                    matches.size(), restaurantId, matches.get(0).getId());
        }
        return matches.stream().findFirst();
    }

    /**
     * Canonicalize a phone number for CROSS-FORMAT matching in {@link #findCustomerByPhone} — never for
     * what gets PERSISTED (the wizard still stores {@code subscriber.phone} via {@link #normalizePhone},
     * untouched by this feature, so the completeRegistration summary still shows the customer back
     * whatever they typed).
     *
     * <p><b>Documented assumption</b> (Qahvoon is an Uzbekistan-only deployment today): every phone
     * belongs to the "+998" country code with a 9-digit national significant number (NSN), e.g.
     * "901234567". Concretely, after stripping every non-digit character:
     * <ul>
     *   <li>12 digits starting "998" (country code already present) → the NSN is the last 9;</li>
     *   <li>10 digits starting "0" (domestic trunk prefix) → the NSN is the last 9;</li>
     *   <li>9 digits → already a bare NSN;</li>
     *   <li>anything else → no confident NSN (a non-Uzbek or malformed number) — falls back to
     *       {@code "+" + digits} so two differently-PUNCTUATED copies of the very same string still
     *       compare equal, without inventing a "998" prefix this method cannot actually confirm.</li>
     * </ul>
     * A recognized Uzbek number always canonicalizes to {@code "+998" + NSN}, regardless of whether the
     * input was local ("901234567"), spaced ("+998 90 123 45 67"), or already E.164
     * ("+998901234567") — which is exactly what lets all three link to the same stored customer.
     *
     * <p>Out of scope, by design: a genuinely foreign number that happens to ALSO be 9 or 12 digits long
     * (this heuristic has no way to distinguish it from an Uzbek one). A multi-country tenant base would
     * need a real E.164 library (e.g. Google's libphonenumber) in place of this hand-rolled rule.
     */
    static String canonicalizePhoneForMatching(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^\\d]", "");
        if (digits.isEmpty()) return "";
        if (digits.length() == 12 && digits.startsWith("998")) {
            return "+998" + digits.substring(3);
        }
        if (digits.length() == 10 && digits.startsWith("0")) {
            return "+998" + digits.substring(1);
        }
        if (digits.length() == 9) {
            return "+998" + digits;
        }
        return "+" + digits;
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

package com.elcafe.modules.notification.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import com.elcafe.common.tenant.TenantContext;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.utils.LogSanitizer;
import com.elcafe.modules.notification.config.TelegramBotRegistry;
import com.elcafe.modules.telegram.entity.TelegramBotConfig;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import com.elcafe.modules.telegram.entity.TelegramSubscriberLocation;
import com.elcafe.modules.telegram.repository.TelegramBotConfigRepository;
import com.elcafe.modules.telegram.repository.TelegramSubscriberLocationRepository;
import com.elcafe.modules.telegram.repository.TelegramSubscriberRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Telegram seller-bot service.
 *
 * On first contact the bot walks the user through a registration wizard:
 *   1. Full name
 *   2. Phone number  (Share Contact button or manual entry)
 *   3. Birthday      (optional — /skip)
 *   4. Delivery location(s)  (Share Location button, repeatable)
 *
 * Conversation state is persisted in {@code telegram_subscribers.conversation_state}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService {

    // States
    private static final String STATE_AWAITING_NAME           = "AWAITING_NAME";
    private static final String STATE_AWAITING_PHONE          = "AWAITING_PHONE";
    private static final String STATE_AWAITING_BIRTHDAY       = "AWAITING_BIRTHDAY";
    private static final String STATE_AWAITING_LOCATION       = "AWAITING_LOCATION";
    private static final String STATE_AWAITING_MORE_LOCATIONS = "AWAITING_MORE_LOCATIONS";
    private static final String STATE_REGISTERED              = "REGISTERED";

    private static final DateTimeFormatter BIRTHDAY_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final TelegramBotConfigRepository configRepository;
    private final TelegramSubscriberRepository subscriberRepository;
    private final TelegramSubscriberLocationRepository locationRepository;
    private final CustomerRepository customerRepository;
    private final TelegramBotRegistry botRegistry;

    @Value("${branding.name:Qahvoon}")
    private String brandName;

    /**
     * Public HTTPS base URL of the customer Mini App (the online menu served for Telegram). When set, the
     * bot shows a "Menyu / Buyurtma" WebApp button that opens the menu in-app, scoped to this restaurant.
     * Empty (the default) hides the button — Telegram only renders WebApp buttons for HTTPS URLs, so a
     * non-HTTPS value is treated as absent.
     */
    @Value("${app.telegram.miniapp.base-url:}")
    private String miniAppBaseUrl;

    /**
     * V164: Telegram is a per-tenant channel — every restaurant runs its OWN bot with its own token,
     * so the service holds one running bot per restaurant instead of a single global one. The bot
     * instance that receives an update IS the tenant: each registration captures its restaurantId and
     * binds it to {@link TenantContext} before any data access, which is what scopes the polling
     * thread (it has no request, so nothing else would).
     */
    private record BotHandle(Long restaurantId, String token, QahvoonBot bot, BotSession session) { }

    private final Map<Long, BotHandle> botsByRestaurant = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        initializeBots();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Start one bot per restaurant that has an active configuration.
     *
     * <p>Runs at boot with no {@link TenantContext} bound, which is deliberate: this is the one place
     * that must read ACROSS tenants (a null tenant leaves the restaurantFilter disabled), so it can
     * enumerate every restaurant's config. Everything downstream is tenant-bound.
     */
    public synchronized void initializeBots() {
        stopAllBots();

        List<TelegramBotConfig> configs = configRepository.findByIsActiveTrue();
        if (configs.isEmpty()) {
            log.info("No active Telegram bot configuration found. No customer bot will start.");
            return;
        }
        for (TelegramBotConfig config : configs) {
            startBotFor(config);
        }
        log.info("Telegram customer bots running for {} restaurant(s)", botsByRestaurant.size());
    }

    private void startBotFor(TelegramBotConfig config) {
        Long restaurantId = config.getRestaurantId();
        if (restaurantId == null) {
            log.warn("Telegram config id={} has no restaurant; skipping", config.getId());
            return;
        }
        if (config.getBotToken() == null || config.getBotToken().isEmpty()) {
            log.warn("Telegram bot token is empty for restaurant {}. Bot will not be registered.", restaurantId);
            return;
        }
        if (!Boolean.TRUE.equals(config.getIsActive())) {
            return;
        }

        // The handler closes over this restaurant's id — that is how an update arriving on the
        // polling thread knows which tenant it belongs to.
        QahvoonBot newBot = new QahvoonBot(config.getBotToken(), config.getBotUsername(),
                update -> handleUpdate(restaurantId, update));
        BotSession session = botRegistry.registerBot(newBot);
        if (session != null) {
            botsByRestaurant.put(restaurantId, new BotHandle(restaurantId, config.getBotToken(), newBot, session));
            log.info("Telegram customer bot registered for restaurant {}: @{}",
                    restaurantId, config.getBotUsername());
        }
    }

    /** Stop the bot of one restaurant (no-op when it has none running). */
    public synchronized void stopBot(Long restaurantId) {
        BotHandle handle = botsByRestaurant.remove(restaurantId);
        if (handle != null) {
            botRegistry.unregisterBot(handle.token(), handle.bot());
            log.info("Telegram customer bot stopped for restaurant {}", restaurantId);
        }
    }

    public synchronized void stopAllBots() {
        for (Long restaurantId : Set.copyOf(botsByRestaurant.keySet())) {
            stopBot(restaurantId);
        }
    }

    /** Restart just one restaurant's bot — called when that restaurant edits its configuration. */
    public synchronized void restartBot(Long restaurantId) {
        log.info("Restarting Telegram customer bot for restaurant {}", restaurantId);
        stopBot(restaurantId);
        configRepository.findByRestaurantIdAndIsActiveTrue(restaurantId).ifPresent(this::startBotFor);
    }

    public boolean isReady(Long restaurantId) {
        BotHandle handle = botsByRestaurant.get(restaurantId);
        return handle != null && botRegistry.isRegistered(handle.token());
    }

    // -------------------------------------------------------------------------
    // Update routing
    // -------------------------------------------------------------------------

    /**
     * Entry point for one restaurant's bot. Binds {@link TenantContext} for the duration so every
     * repository call below is scoped by the §3.4 restaurantFilter — the polling thread has no
     * request, so without this the wizard would read and write across tenants. Cleared in
     * {@code finally} so nothing leaks onto the next update on this pooled thread.
     */
    private void handleUpdate(Long restaurantId, Update update) {
        if (!update.hasMessage()) return;
        TenantContext.setRestaurantId(restaurantId);
        try {
            handleMessage(update.getMessage());
        } catch (Exception e) {
            log.error("Error handling Telegram update for restaurant {}: {}", restaurantId, e.getMessage(), e);
        } finally {
            TenantContext.clear();
        }
    }

    private void handleMessage(Message message) {
        User telegramUser = message.getFrom();
        Long chatId = message.getChatId();

        // /start always (re-)starts the registration wizard
        if (message.hasText()) {
            String text = message.getText();
            if (text.equals("/start") || text.startsWith("/start ")) {
                startRegistration(telegramUser, chatId);
                return;
            }
            if (text.equals("/help")) { sendHelp(chatId); return; }
            if (text.equals("/status")) { sendStatus(chatId); return; }
            if (text.equals("/order") || text.equals("/menu")) { sendOrderLink(chatId); return; }
        }

        TelegramSubscriber subscriber = subscriberRepository.findByTelegramUserId(chatId).orElse(null);
        if (subscriber == null) {
            startRegistration(telegramUser, chatId);
            return;
        }

        subscriber.setLastInteractionAt(OffsetDateTime.now(ZoneOffset.UTC));

        String state = subscriber.getConversationState();
        if (state == null) state = STATE_AWAITING_NAME;

        switch (state) {
            case STATE_AWAITING_NAME -> {
                if (message.hasText()) handleNameInput(subscriber, chatId, message.getText());
                else sendNamePrompt(chatId);
            }
            case STATE_AWAITING_PHONE -> {
                if (message.hasContact())       handleContactReceived(subscriber, chatId, message.getContact());
                else if (message.hasText())     handlePhoneTextInput(subscriber, chatId, message.getText());
                else                            sendPhonePrompt(chatId, subscriber.getDisplayName());
            }
            case STATE_AWAITING_BIRTHDAY -> {
                if (message.hasText())  handleBirthdayInput(subscriber, chatId, message.getText());
                else                    sendBirthdayPrompt(chatId);
            }
            case STATE_AWAITING_LOCATION -> {
                if (message.hasLocation())                              handleLocationReceived(subscriber, chatId, message.getLocation());
                else if (message.hasText() && message.getText().equals("/skip")) completeRegistration(subscriber, chatId);
                else                                                    sendLocationPrompt(chatId);
            }
            case STATE_AWAITING_MORE_LOCATIONS -> {
                if (message.hasLocation()) {
                    handleLocationReceived(subscriber, chatId, message.getLocation());
                } else if (message.hasText()) {
                    String text = message.getText();
                    if (text.startsWith("📍")) {
                        subscriber.setConversationState(STATE_AWAITING_LOCATION);
                        subscriberRepository.save(subscriber);
                        sendLocationPrompt(chatId);
                    } else if (text.startsWith("✅")) {
                        completeRegistration(subscriber, chatId);
                    } else {
                        sendMoreLocationsPrompt(chatId, locationRepository.countBySubscriber(subscriber));
                    }
                }
            }
            case STATE_REGISTERED -> {
                subscriber.setLastInteractionAt(OffsetDateTime.now(ZoneOffset.UTC));
                subscriberRepository.save(subscriber);
                sendMainMenu(subscriber, chatId);
            }
            default -> startRegistration(telegramUser, chatId);
        }
    }

    // -------------------------------------------------------------------------
    // Registration wizard steps
    // -------------------------------------------------------------------------

    private void startRegistration(User telegramUser, Long chatId) {
        TelegramSubscriber subscriber = subscriberRepository.findByTelegramUserId(chatId)
                .orElseGet(() -> TelegramSubscriber.builder()
                        // Bound by handleUpdate from the bot instance that received this update.
                        .restaurantId(TenantContext.getRestaurantId())
                        .telegramUserId(chatId)
                        .subscribedAt(OffsetDateTime.now(ZoneOffset.UTC))
                        .isActive(true)
                        .isBlocked(false)
                        .build());

        subscriber.setUsername(telegramUser.getUserName());
        subscriber.setFirstName(telegramUser.getFirstName());
        subscriber.setLastName(telegramUser.getLastName());
        subscriber.setLanguageCode(telegramUser.getLanguageCode());
        subscriber.setLastInteractionAt(OffsetDateTime.now(ZoneOffset.UTC));
        subscriber.setConversationState(STATE_AWAITING_NAME);
        subscriberRepository.save(subscriber);

        log.info("Telegram subscriber registration started: chatId={}", chatId);
        sendNamePrompt(chatId);
    }

    private void handleNameInput(TelegramSubscriber subscriber, Long chatId, String text) {
        String name = text.trim();
        if (name.length() < 2 || name.length() > 100) {
            execute(chatId, "Iltimos, to'liq ismingizni kiriting (2–100 belgi):");
            return;
        }
        subscriber.setDisplayName(name);
        subscriber.setConversationState(STATE_AWAITING_PHONE);
        subscriberRepository.save(subscriber);
        sendPhonePrompt(chatId, name);
    }

    private void handleContactReceived(TelegramSubscriber subscriber, Long chatId, Contact contact) {
        subscriber.setPhone(normalizePhone(contact.getPhoneNumber()));
        subscriber.setConversationState(STATE_AWAITING_BIRTHDAY);
        subscriberRepository.save(subscriber);
        sendBirthdayPrompt(chatId);
    }

    private void handlePhoneTextInput(TelegramSubscriber subscriber, Long chatId, String text) {
        String phone = normalizePhone(text.trim());
        if (phone.replaceAll("[^\\d]", "").length() < 7) {
            execute(chatId, "❌ Iltimos, to'g'ri telefon raqam kiriting (masalan: +998901234567):");
            return;
        }
        subscriber.setPhone(phone);
        subscriber.setConversationState(STATE_AWAITING_BIRTHDAY);
        subscriberRepository.save(subscriber);
        sendBirthdayPrompt(chatId);
    }

    private void handleBirthdayInput(TelegramSubscriber subscriber, Long chatId, String text) {
        if (!text.trim().equals("/skip")) {
            LocalDate birthday;
            try {
                birthday = LocalDate.parse(text.trim(), BIRTHDAY_FMT);
            } catch (DateTimeParseException e) {
                execute(chatId,
                        "❌ Format noto'g'ri. DD.MM.YYYY ko'rinishida kiriting " +
                        "(masalan: 15.03.1990) yoki /skip yozing:");
                return;
            }
            subscriber.setBirthDate(birthday);
        }
        subscriber.setConversationState(STATE_AWAITING_LOCATION);
        subscriberRepository.save(subscriber);
        sendLocationPrompt(chatId);
    }

    private void handleLocationReceived(TelegramSubscriber subscriber, Long chatId, Location location) {
        long existingCount = locationRepository.countBySubscriber(subscriber);

        TelegramSubscriberLocation loc = TelegramSubscriberLocation.builder()
                .restaurantId(subscriber.getRestaurantId())
                .subscriber(subscriber)
                .latitude(location.getLatitude().doubleValue())
                .longitude(location.getLongitude().doubleValue())
                .isDefault(existingCount == 0)
                .build();
        locationRepository.save(loc);

        subscriber.setConversationState(STATE_AWAITING_MORE_LOCATIONS);
        subscriberRepository.save(subscriber);

        sendMoreLocationsPrompt(chatId, existingCount + 1);
    }

    private void completeRegistration(TelegramSubscriber subscriber, Long chatId) {
        // Attempt to link to existing customer account by phone
        if (subscriber.getPhone() != null && subscriber.getCustomer() == null) {
            // V150: the Telegram bot is a global channel with no restaurant context, so link to the
            // customer's primary record (oldest row for the phone).
            customerRepository.findFirstByPhoneOrderByIdAsc(subscriber.getPhone()).ifPresent(customer -> {
                subscriber.setCustomer(customer);
                log.info("Linked Telegram subscriber {} to customer {}",
                        subscriber.getTelegramUserId(), customer.getId());
            });
        }
        subscriber.setConversationState(STATE_REGISTERED);
        subscriberRepository.save(subscriber);
        log.info("Telegram subscriber registered: chatId={}, phone={}", chatId, LogSanitizer.phone(subscriber.getPhone()));
        sendRegistrationSummary(subscriber, chatId);
    }

    // -------------------------------------------------------------------------
    // Prompt builders
    // -------------------------------------------------------------------------

    private void sendNamePrompt(Long chatId) {
        SendMessage msg = buildMessage(chatId,
                "👋 <b>Xush kelibsiz " + brandName + "'ga!</b>\n\n" +
                "Sizni yaxshiroq tanish uchun bir nechta savol beramiz.\n\n" +
                "Ismingizni kiriting (to'liq ism yoki laqab):");
        msg.setReplyMarkup(removeKeyboard());
        execute(msg);
    }

    private void sendPhonePrompt(Long chatId, String name) {
        SendMessage msg = buildMessage(chatId,
                "Juda yaxshi, <b>" + esc(name) + "</b>! 😊\n\n" +
                "Telefon raqamingizni ulashing yoki qo'lda kiriting:");
        msg.setReplyMarkup(phoneKeyboard());
        execute(msg);
    }

    private void sendBirthdayPrompt(Long chatId) {
        SendMessage msg = buildMessage(chatId,
                "📅 Tug'ilgan kuningizni kiriting.\n" +
                "Format: <b>DD.MM.YYYY</b>  (masalan: 15.03.1990)\n\n" +
                "Ulashishni istmasangiz /skip yozing.");
        msg.setReplyMarkup(removeKeyboard());
        execute(msg);
    }

    private void sendLocationPrompt(Long chatId) {
        SendMessage msg = buildMessage(chatId,
                "📍 Yetkazib berish manzilingizni ulashing.\n\n" +
                "Quyidagi tugmani bosing yoki 📎 → <b>Location</b> ni tanlang.\n\n" +
                "Manzil kiritishni o'tkazib yuborish uchun /skip yozing.");
        msg.setReplyMarkup(locationKeyboard());
        execute(msg);
    }

    private void sendMoreLocationsPrompt(Long chatId, long savedCount) {
        SendMessage msg = buildMessage(chatId,
                "✅ Manzil saqlandi! (Jami: <b>" + savedCount + "</b> ta)\n\n" +
                "Yana manzil qo'shmoqchimisiz?");
        msg.setReplyMarkup(moreLocationsKeyboard());
        execute(msg);
    }

    private void sendRegistrationSummary(TelegramSubscriber subscriber, Long chatId) {
        List<TelegramSubscriberLocation> locations = locationRepository.findAllBySubscriber(subscriber);

        String name = subscriber.getDisplayName() != null
                ? subscriber.getDisplayName() : subscriber.getFirstName();

        StringBuilder sb = new StringBuilder();
        sb.append("🎉 <b>Ro'yxatdan o'tish muvaffaqiyatli yakunlandi!</b>\n\n");
        sb.append("📋 <b>Sizning ma'lumotlaringiz:</b>\n");
        sb.append("👤 Ism: ").append(esc(name)).append("\n");
        sb.append("📱 Telefon: ")
          .append(subscriber.getPhone() != null ? esc(subscriber.getPhone()) : "kiritilmagan")
          .append("\n");
        sb.append("🎂 Tug'ilgan kun: ")
          .append(subscriber.getBirthDate() != null
                  ? subscriber.getBirthDate().format(BIRTHDAY_FMT) : "kiritilmagan")
          .append("\n");
        if (!locations.isEmpty()) {
            sb.append("📍 Manzillar: ").append(locations.size()).append(" ta saqlangan\n");
        }
        sb.append("\n");
        if (subscriber.getCustomer() != null) {
            sb.append("✨ Hisobingiz mijoz profili bilan bog'landi!\n\n");
        }
        sb.append("🎁 Endi siz aksiyalar, chegirmalar va tug'ilgan kun sovg'alari haqida xabar olasiz.\n\n");
        sb.append("/status — holatini ko'rish\n");
        sb.append("/start  — ma'lumotlarni yangilash\n");
        sb.append("/help   — yordam");

        SendMessage msg = buildMessage(chatId, sb.toString());
        // Leave the customer on the persistent "order" button when the Mini App is live; otherwise clear
        // the wizard's share-contact/location keyboard.
        ReplyKeyboardMarkup orderKeyboard = orderKeyboard(subscriber.getRestaurantId());
        if (orderKeyboard != null) {
            msg.setReplyMarkup(orderKeyboard);
        } else {
            msg.setReplyMarkup(removeKeyboard());
        }
        execute(msg);
    }

    private void sendMainMenu(TelegramSubscriber subscriber, Long chatId) {
        String name = subscriber.getDisplayName() != null
                ? subscriber.getDisplayName() : subscriber.getFirstName();
        SendMessage msg = buildMessage(chatId,
                "👋 Salom, <b>" + esc(name) + "</b>!\n\n" +
                orderHintLine() +
                "/status — holatini ko'rish\n" +
                "/start  — ma'lumotlarni yangilash\n" +
                "/help   — yordam");
        ReplyKeyboardMarkup orderKeyboard = orderKeyboard(subscriber.getRestaurantId());
        if (orderKeyboard != null) {
            msg.setReplyMarkup(orderKeyboard);
        }
        execute(msg);
    }

    /**
     * Send the Mini App "order" button on demand ({@code /order}, {@code /menu}). restaurantId comes from
     * {@link TenantContext}, bound by {@link #handleUpdate} to the bot that received this message.
     */
    private void sendOrderLink(Long chatId) {
        ReplyKeyboardMarkup keyboard = orderKeyboard(TenantContext.getRestaurantId());
        if (keyboard == null) {
            execute(chatId, "🍽 Onlayn buyurtma tez orada ishga tushadi.");
            return;
        }
        SendMessage msg = buildMessage(chatId,
                "🍽 <b>Menyu</b>\n\nPastdagi «Menyu / Buyurtma» tugmasini bosing va buyurtma bering.");
        msg.setReplyMarkup(keyboard);
        execute(msg);
    }

    private String orderHintLine() {
        return isMiniAppEnabled()
                ? "🍽 <b>Buyurtma berish</b> — pastdagi «Menyu / Buyurtma» tugmasini bosing\n\n"
                : "";
    }

    private void sendHelp(Long chatId) {
        execute(chatId,
                "📚 <b>Yordam</b>\n\n" +
                "Bu bot orqali siz quyidagilarni olishingiz mumkin:\n\n" +
                "🎁 <b>Aksiyalar</b> — maxsus takliflar va chegirmalar\n" +
                "🎂 <b>Tug'ilgan kun</b> — bayram kunida sovg'alar\n" +
                "🍽 <b>Yangiliklar</b> — yangi taomlar haqida\n" +
                "📦 <b>Buyurtmalar</b> — buyurtma holati\n\n" +
                orderHintLine() +
                "Buyruqlar:\n" +
                "/order  — menyu va buyurtma\n" +
                "/start  — ro'yxatdan o'tish / yangilash\n" +
                "/status — holatini ko'rish\n" +
                "/help   — yordam");
    }

    private void sendStatus(Long chatId) {
        TelegramSubscriber sub = subscriberRepository.findByTelegramUserId(chatId).orElse(null);
        if (sub == null || !STATE_REGISTERED.equals(sub.getConversationState())) {
            execute(chatId, "❌ Siz hali ro'yxatdan o'tmagansiz. /start buyrug'ini yuboring.");
        } else {
            String name = sub.getDisplayName() != null ? sub.getDisplayName() : sub.getFirstName();
            execute(chatId,
                    "✅ <b>Siz ro'yxatdan o'tgansiz!</b>\n\n" +
                    "👤 " + esc(name) + "\n" +
                    "📱 " + (sub.getPhone() != null ? esc(sub.getPhone()) : "telefon kiritilmagan") + "\n" +
                    "🎂 " + (sub.getBirthDate() != null
                            ? sub.getBirthDate().format(BIRTHDAY_FMT) : "tug'ilgan kun kiritilmagan"));
        }
    }

    // -------------------------------------------------------------------------
    // Keyboard builders
    // -------------------------------------------------------------------------

    private ReplyKeyboardMarkup phoneKeyboard() {
        KeyboardButton btn = new KeyboardButton("📱 Telefon raqamni ulashing");
        btn.setRequestContact(true);
        KeyboardRow row = new KeyboardRow();
        row.add(btn);
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);
        return markup;
    }

    private ReplyKeyboardMarkup locationKeyboard() {
        KeyboardButton btn = new KeyboardButton("📍 Manzilni ulashing");
        btn.setRequestLocation(true);
        KeyboardRow row = new KeyboardRow();
        row.add(btn);
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);
        return markup;
    }

    private ReplyKeyboardMarkup moreLocationsKeyboard() {
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("📍 Yana manzil qo'shish"));
        row.add(new KeyboardButton("✅ Tayyor"));
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);
        return markup;
    }

    private ReplyKeyboardRemove removeKeyboard() {
        ReplyKeyboardRemove remove = new ReplyKeyboardRemove();
        remove.setRemoveKeyboard(true);
        return remove;
    }

    /**
     * A one-row reply keyboard whose button opens this restaurant's Mini App menu, or {@code null} when no
     * Mini App URL is configured. Telegram only renders WebApp buttons over HTTPS, so a blank or non-HTTPS
     * value hides it rather than sending a button Telegram would reject.
     */
    private ReplyKeyboardMarkup orderKeyboard(Long restaurantId) {
        WebAppInfo webApp = miniAppFor(restaurantId);
        if (webApp == null) {
            return null;
        }
        KeyboardButton btn = new KeyboardButton("🍽 Menyu / Buyurtma");
        btn.setWebApp(webApp);
        KeyboardRow row = new KeyboardRow();
        row.add(btn);
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        return markup;
    }

    private WebAppInfo miniAppFor(Long restaurantId) {
        if (restaurantId == null || !isMiniAppEnabled()) {
            return null;
        }
        // The Mini App (the customer menu) opens scoped to this restaurant via the param; it then logs
        // the customer in by verifying signed initData against this restaurant's bot token server-side,
        // so the param is only a hint, not the trust boundary. restaurantId is not sensitive — it already
        // appears in every public menu URL.
        String sep = miniAppBaseUrl.contains("?") ? "&" : "?";
        return new WebAppInfo(miniAppBaseUrl + sep + "restaurantId=" + restaurantId);
    }

    private boolean isMiniAppEnabled() {
        return miniAppBaseUrl != null && miniAppBaseUrl.startsWith("https://");
    }

    // -------------------------------------------------------------------------
    // Execution helpers
    // -------------------------------------------------------------------------

    private SendMessage buildMessage(Long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);
        msg.setParseMode("HTML");
        return msg;
    }

    private void execute(Long chatId, String text) {
        execute(buildMessage(chatId, text));
    }

    /**
     * Wizard replies go out through the bot of the restaurant currently bound to {@link TenantContext}
     * — i.e. the same bot the update arrived on, so a customer always hears back from the restaurant
     * they messaged.
     */
    private void execute(SendMessage msg) {
        QahvoonBot sender = botFor(TenantContext.getRestaurantId());
        if (sender == null) return;
        try {
            sender.execute(msg);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message to {}: {}", msg.getChatId(), e.getMessage());
        }
    }

    /** The running bot of one restaurant, or null when that restaurant has none. */
    private QahvoonBot botFor(Long restaurantId) {
        if (restaurantId == null) return null;
        BotHandle handle = botsByRestaurant.get(restaurantId);
        return handle != null ? handle.bot() : null;
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^\\d+]", "");
        return digits.startsWith("+") ? digits : "+" + digits;
    }

    private String esc(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // -------------------------------------------------------------------------
    // Public outbound API (used by other services to push notifications)
    // -------------------------------------------------------------------------

    public Integer sendMessage(Long restaurantId, Long chatId, String message) {
        QahvoonBot sender = botFor(restaurantId);
        if (sender == null) {
            log.debug("No Telegram bot running for restaurant {}, skipping message to chatId {}",
                    restaurantId, chatId);
            return null;
        }
        try {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId.toString());
            sendMessage.setText(message);
            sendMessage.setParseMode("HTML");
            Message sent = sender.execute(sendMessage);
            return sent.getMessageId();
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message to chatId {}: {}", chatId, e.getMessage());
            return null;
        }
    }

    public Integer sendMessageWithButtons(Long restaurantId, Long chatId, String message,
                                          List<List<Map<String, String>>> buttons) {
        QahvoonBot sender = botFor(restaurantId);
        if (sender == null) return null;
        try {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId.toString());
            sendMessage.setText(message);
            sendMessage.setParseMode("HTML");

            if (buttons != null && !buttons.isEmpty()) {
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                for (List<Map<String, String>> row : buttons) {
                    List<InlineKeyboardButton> keyboardRow = new ArrayList<>();
                    for (Map<String, String> btn : row) {
                        InlineKeyboardButton button = new InlineKeyboardButton();
                        button.setText(btn.getOrDefault("text", "Button"));
                        if (btn.containsKey("url"))           button.setUrl(btn.get("url"));
                        else if (btn.containsKey("callback_data")) button.setCallbackData(btn.get("callback_data"));
                        keyboardRow.add(button);
                    }
                    keyboard.add(keyboardRow);
                }
                markup.setKeyboard(keyboard);
                sendMessage.setReplyMarkup(markup);
            }

            Message sent = sender.execute(sendMessage);
            return sent.getMessageId();
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message with buttons to chatId {}: {}", chatId, e.getMessage());
            return null;
        }
    }

    public Integer sendPhoto(Long restaurantId, Long chatId, String photoUrl, String caption) {
        QahvoonBot sender = botFor(restaurantId);
        if (sender == null) return null;
        try {
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(new InputFile(photoUrl));
            if (caption != null && !caption.isEmpty()) {
                sendPhoto.setCaption(caption);
                sendPhoto.setParseMode("HTML");
            }
            Message sent = sender.execute(sendPhoto);
            return sent.getMessageId();
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram photo to chatId {}: {}", chatId, e.getMessage());
            return null;
        }
    }

    public Integer sendPhotoWithButtons(Long restaurantId, Long chatId, String photoUrl, String caption,
                                        List<List<Map<String, String>>> buttons) {
        QahvoonBot sender = botFor(restaurantId);
        if (sender == null) return null;
        try {
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(new InputFile(photoUrl));
            if (caption != null && !caption.isEmpty()) {
                sendPhoto.setCaption(caption);
                sendPhoto.setParseMode("HTML");
            }

            if (buttons != null && !buttons.isEmpty()) {
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                for (List<Map<String, String>> row : buttons) {
                    List<InlineKeyboardButton> keyboardRow = new ArrayList<>();
                    for (Map<String, String> btn : row) {
                        InlineKeyboardButton button = new InlineKeyboardButton();
                        button.setText(btn.getOrDefault("text", "Button"));
                        if (btn.containsKey("url"))                button.setUrl(btn.get("url"));
                        else if (btn.containsKey("callback_data")) button.setCallbackData(btn.get("callback_data"));
                        keyboardRow.add(button);
                    }
                    keyboard.add(keyboardRow);
                }
                markup.setKeyboard(keyboard);
                sendPhoto.setReplyMarkup(markup);
            }

            Message sent = sender.execute(sendPhoto);
            return sent.getMessageId();
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram photo with buttons to chatId {}: {}", chatId, e.getMessage());
            return null;
        }
    }

    public boolean sendStockAlert(Long restaurantId, Long chatId, String restaurantName,
                                  String alertType, String items) {
        String emoji = alertType.equals("LOW_STOCK") ? "🔴" : "🟡";
        String title = alertType.equals("LOW_STOCK") ? "Низкий уровень запасов" : "Требуется заказ";
        String message = String.format(
                "%s <b>%s</b>\n\n🏪 <b>%s</b>\n\n%s\n\n⏰ %s",
                emoji, title, restaurantName, items,
                java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        return sendMessage(restaurantId, chatId, message) != null;
    }

    // -------------------------------------------------------------------------
    // Inner bot — forwards all updates to TelegramBotService.handleUpdate()
    // -------------------------------------------------------------------------

    private static class QahvoonBot extends TelegramLongPollingBot {
        private final String username;
        private final Consumer<Update> updateHandler;

        QahvoonBot(String token, String username, Consumer<Update> updateHandler) {
            super(token);
            this.username = username;
            this.updateHandler = updateHandler;
        }

        @Override public String getBotUsername() { return username; }

        @Override
        public void onUpdateReceived(Update update) {
            if (updateHandler != null) updateHandler.accept(update);
        }
    }
}

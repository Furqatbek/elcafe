package com.elcafe.modules.notification.service;

import com.elcafe.modules.customer.repository.CustomerRepository;
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

    private ElCafeBot bot;
    private BotSession botSession;
    private String currentToken;

    @PostConstruct
    public void init() {
        initializeBot();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public synchronized void initializeBot() {
        stopBot();

        Optional<TelegramBotConfig> configOpt = configRepository.findByIsActiveTrue();
        if (configOpt.isEmpty()) {
            log.info("No active Telegram bot configuration found. Bot will not start.");
            return;
        }

        TelegramBotConfig config = configOpt.get();
        if (config.getBotToken() == null || config.getBotToken().isEmpty()) {
            log.warn("Telegram bot token is empty. Bot will not be registered.");
            return;
        }
        if (!Boolean.TRUE.equals(config.getIsActive())) {
            log.info("Telegram bot is disabled in configuration.");
            return;
        }

        this.currentToken = config.getBotToken();
        bot = new ElCafeBot(config.getBotToken(), config.getBotUsername(), this::handleUpdate);
        botSession = botRegistry.registerBot(bot);
        if (botSession != null) {
            log.info("Telegram customer bot registered successfully: @{}", config.getBotUsername());
        }
    }

    public synchronized void stopBot() {
        if (currentToken != null && bot != null) {
            botRegistry.unregisterBot(currentToken, bot);
            log.info("Telegram customer bot stopped");
        }
        bot = null;
        botSession = null;
        currentToken = null;
    }

    public void restartBot() {
        log.info("Restarting Telegram customer bot with new configuration...");
        initializeBot();
    }

    public boolean isReady() {
        return bot != null && currentToken != null && botRegistry.isRegistered(currentToken);
    }

    // -------------------------------------------------------------------------
    // Update routing
    // -------------------------------------------------------------------------

    private void handleUpdate(Update update) {
        if (!update.hasMessage()) return;
        try {
            handleMessage(update.getMessage());
        } catch (Exception e) {
            log.error("Error handling Telegram update: {}", e.getMessage(), e);
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
                .orElse(TelegramSubscriber.builder()
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
        log.info("Telegram subscriber registered: chatId={}, phone={}", chatId, subscriber.getPhone());
        sendRegistrationSummary(subscriber, chatId);
    }

    // -------------------------------------------------------------------------
    // Prompt builders
    // -------------------------------------------------------------------------

    private void sendNamePrompt(Long chatId) {
        SendMessage msg = buildMessage(chatId,
                "👋 <b>Xush kelibsiz ElCafe'ga!</b>\n\n" +
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
        msg.setReplyMarkup(removeKeyboard());
        execute(msg);
    }

    private void sendMainMenu(TelegramSubscriber subscriber, Long chatId) {
        String name = subscriber.getDisplayName() != null
                ? subscriber.getDisplayName() : subscriber.getFirstName();
        execute(chatId,
                "👋 Salom, <b>" + esc(name) + "</b>!\n\n" +
                "/status — holatini ko'rish\n" +
                "/start  — ma'lumotlarni yangilash\n" +
                "/help   — yordam");
    }

    private void sendHelp(Long chatId) {
        execute(chatId,
                "📚 <b>Yordam</b>\n\n" +
                "Bu bot orqali siz quyidagilarni olishingiz mumkin:\n\n" +
                "🎁 <b>Aksiyalar</b> — maxsus takliflar va chegirmalar\n" +
                "🎂 <b>Tug'ilgan kun</b> — bayram kunida sovg'alar\n" +
                "🍽 <b>Yangiliklar</b> — yangi taomlar haqida\n" +
                "📦 <b>Buyurtmalar</b> — buyurtma holati\n\n" +
                "Buyruqlar:\n" +
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

    private void execute(SendMessage msg) {
        if (bot == null) return;
        try {
            bot.execute(msg);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message to {}: {}", msg.getChatId(), e.getMessage());
        }
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

    public Integer sendMessage(Long chatId, String message) {
        if (!isReady()) {
            log.debug("Telegram bot is not running, skipping message to chatId: {}", chatId);
            return null;
        }
        try {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId.toString());
            sendMessage.setText(message);
            sendMessage.setParseMode("HTML");
            Message sent = bot.execute(sendMessage);
            return sent.getMessageId();
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message to chatId {}: {}", chatId, e.getMessage());
            return null;
        }
    }

    public Integer sendMessageWithButtons(Long chatId, String message, List<List<Map<String, String>>> buttons) {
        if (!isReady()) return null;
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

            Message sent = bot.execute(sendMessage);
            return sent.getMessageId();
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message with buttons to chatId {}: {}", chatId, e.getMessage());
            return null;
        }
    }

    public Integer sendPhoto(Long chatId, String photoUrl, String caption) {
        if (!isReady()) return null;
        try {
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(new InputFile(photoUrl));
            if (caption != null && !caption.isEmpty()) {
                sendPhoto.setCaption(caption);
                sendPhoto.setParseMode("HTML");
            }
            Message sent = bot.execute(sendPhoto);
            return sent.getMessageId();
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram photo to chatId {}: {}", chatId, e.getMessage());
            return null;
        }
    }

    public Integer sendPhotoWithButtons(Long chatId, String photoUrl, String caption,
                                        List<List<Map<String, String>>> buttons) {
        if (!isReady()) return null;
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

            Message sent = bot.execute(sendPhoto);
            return sent.getMessageId();
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram photo with buttons to chatId {}: {}", chatId, e.getMessage());
            return null;
        }
    }

    public boolean sendStockAlert(Long chatId, String restaurantName, String alertType, String items) {
        String emoji = alertType.equals("LOW_STOCK") ? "🔴" : "🟡";
        String title = alertType.equals("LOW_STOCK") ? "Низкий уровень запасов" : "Требуется заказ";
        String message = String.format(
                "%s <b>%s</b>\n\n🏪 <b>%s</b>\n\n%s\n\n⏰ %s",
                emoji, title, restaurantName, items,
                java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        return sendMessage(chatId, message) != null;
    }

    // -------------------------------------------------------------------------
    // Inner bot — forwards all updates to TelegramBotService.handleUpdate()
    // -------------------------------------------------------------------------

    private static class ElCafeBot extends TelegramLongPollingBot {
        private final String username;
        private final Consumer<Update> updateHandler;

        ElCafeBot(String token, String username, Consumer<Update> updateHandler) {
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

package com.elcafe.modules.notification.service;

import com.elcafe.modules.telegram.entity.TelegramBotConfig;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import com.elcafe.modules.telegram.repository.TelegramBotConfigRepository;
import com.elcafe.modules.telegram.repository.TelegramSubscriberRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Telegram Bot Service for sending notifications and managing subscribers.
 * Reads configuration from database (managed via frontend admin panel).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService {

    private final TelegramBotConfigRepository configRepository;
    private final TelegramSubscriberRepository subscriberRepository;

    private ElCafeBot bot;
    private BotSession botSession;
    private String currentToken;
    private String welcomeMessage;

    @PostConstruct
    public void init() {
        initializeBot();
    }

    /**
     * Initialize or reinitialize the bot from database configuration.
     * Called on startup and when configuration is updated from frontend.
     */
    public synchronized void initializeBot() {
        // Stop existing bot if running
        stopBot();

        // Load config from database
        Optional<TelegramBotConfig> configOpt = configRepository.findByIsActiveTrue();

        if (configOpt.isEmpty()) {
            log.info("No active Telegram bot configuration found in database. Bot will not start.");
            log.info("Configure the bot via Settings -> Telegram in the admin panel.");
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

        try {
            this.currentToken = config.getBotToken();
            this.welcomeMessage = config.getWelcomeMessage();

            bot = new ElCafeBot(
                config.getBotToken(),
                config.getBotUsername(),
                this::handleSubscriberRegistration,
                this::handleSubscriberInteraction,
                this.welcomeMessage
            );

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botSession = botsApi.registerBot(bot);

            log.info("Telegram customer bot registered successfully: @{}", config.getBotUsername());
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram bot: {}", e.getMessage());
        }
    }

    /**
     * Stop the currently running bot.
     */
    public synchronized void stopBot() {
        if (botSession != null && botSession.isRunning()) {
            botSession.stop();
            log.info("Telegram customer bot stopped");
        }
        bot = null;
        botSession = null;
        currentToken = null;
    }

    /**
     * Restart the bot with new configuration from database.
     * Called when configuration is updated via frontend.
     */
    public void restartBot() {
        log.info("Restarting Telegram customer bot with new configuration...");
        initializeBot();
    }

    /**
     * Handle subscriber registration when user sends /start
     */
    private void handleSubscriberRegistration(User user, Long chatId) {
        try {
            TelegramSubscriber subscriber = subscriberRepository.findByTelegramUserId(chatId)
                    .orElse(TelegramSubscriber.builder()
                            .telegramUserId(chatId)
                            .subscribedAt(OffsetDateTime.now(ZoneOffset.UTC))
                            .isActive(true)
                            .isBlocked(false)
                            .build());

            subscriber.setUsername(user.getUserName());
            subscriber.setFirstName(user.getFirstName());
            subscriber.setLastName(user.getLastName());
            subscriber.setLanguageCode(user.getLanguageCode());
            subscriber.setIsActive(true);
            subscriber.setLastInteractionAt(OffsetDateTime.now(ZoneOffset.UTC));

            subscriberRepository.save(subscriber);
            log.info("Telegram subscriber registered/updated: chatId={}, username={}", chatId, user.getUserName());
        } catch (Exception e) {
            log.error("Failed to register Telegram subscriber: chatId={}, error={}", chatId, e.getMessage());
        }
    }

    /**
     * Handle subscriber interaction (update last interaction time)
     */
    private void handleSubscriberInteraction(Long chatId) {
        try {
            subscriberRepository.findByTelegramUserId(chatId).ifPresent(subscriber -> {
                subscriber.setLastInteractionAt(OffsetDateTime.now(ZoneOffset.UTC));
                subscriberRepository.save(subscriber);
            });
        } catch (Exception e) {
            log.error("Failed to update subscriber interaction: chatId={}, error={}", chatId, e.getMessage());
        }
    }

    /**
     * Check if bot is ready to send messages
     */
    public boolean isReady() {
        return bot != null && botSession != null && botSession.isRunning();
    }

    /**
     * Send a text message to a specific chat
     */
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
            log.debug("Message sent to chatId {}: {}", chatId, message.substring(0, Math.min(50, message.length())));
            return sent.getMessageId();
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message to chatId {}: {}", chatId, e.getMessage());
            return null;
        }
    }

    /**
     * Send a message with inline keyboard buttons
     */
    public Integer sendMessageWithButtons(Long chatId, String message, List<List<Map<String, String>>> buttons) {
        if (!isReady()) {
            return null;
        }

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
                        if (btn.containsKey("url")) {
                            button.setUrl(btn.get("url"));
                        } else if (btn.containsKey("callback_data")) {
                            button.setCallbackData(btn.get("callback_data"));
                        }
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

    /**
     * Send a photo with optional caption
     */
    public Integer sendPhoto(Long chatId, String photoUrl, String caption) {
        if (!isReady()) {
            return null;
        }

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

    /**
     * Send a photo with caption and inline keyboard buttons
     */
    public Integer sendPhotoWithButtons(Long chatId, String photoUrl, String caption,
                                         List<List<Map<String, String>>> buttons) {
        if (!isReady()) {
            return null;
        }

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
                        if (btn.containsKey("url")) {
                            button.setUrl(btn.get("url"));
                        } else if (btn.containsKey("callback_data")) {
                            button.setCallbackData(btn.get("callback_data"));
                        }
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

    /**
     * Send a stock alert message
     */
    public boolean sendStockAlert(Long chatId, String restaurantName, String alertType, String items) {
        String emoji = alertType.equals("LOW_STOCK") ? "🔴" : "🟡";
        String title = alertType.equals("LOW_STOCK") ? "Низкий уровень запасов" : "Требуется заказ";

        String message = String.format(
            "%s <b>%s</b>\n\n" +
            "🏪 <b>%s</b>\n\n" +
            "%s\n\n" +
            "⏰ %s",
            emoji, title,
            restaurantName,
            items,
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        );

        return sendMessage(chatId, message) != null;
    }

    /**
     * Inner class for the actual Telegram bot implementation
     */
    private static class ElCafeBot extends TelegramLongPollingBot {
        private final String username;
        private final java.util.function.BiConsumer<User, Long> onSubscribe;
        private final Consumer<Long> onInteraction;
        private final String customWelcomeMessage;

        public ElCafeBot(String token, String username,
                        java.util.function.BiConsumer<User, Long> onSubscribe,
                        Consumer<Long> onInteraction,
                        String customWelcomeMessage) {
            super(token);
            this.username = username;
            this.onSubscribe = onSubscribe;
            this.onInteraction = onInteraction;
            this.customWelcomeMessage = customWelcomeMessage;
        }

        @Override
        public String getBotUsername() {
            return username;
        }

        @Override
        public void onUpdateReceived(Update update) {
            if (update.hasMessage() && update.getMessage().hasText()) {
                String messageText = update.getMessage().getText();
                Long chatId = update.getMessage().getChatId();
                User user = update.getMessage().getFrom();

                // Handle /start command - register subscriber
                if (messageText.equals("/start") || messageText.startsWith("/start ")) {
                    // Register/update subscriber
                    if (onSubscribe != null) {
                        onSubscribe.accept(user, chatId);
                    }

                    String welcomeMessage = customWelcomeMessage != null && !customWelcomeMessage.isEmpty()
                        ? customWelcomeMessage
                        : "👋 <b>Xush kelibsiz!</b>\n\n" +
                          "Siz muvaffaqiyatli ro'yxatdan o'tdingiz va endi bizning " +
                          "aksiyalar, chegirmalar va yangiliklar haqida xabar olasiz.\n\n" +
                          "🎁 <b>Sizni nimalar kutmoqda:</b>\n" +
                          "• Maxsus aksiyalar va chegirmalar\n" +
                          "• Tug'ilgan kun tabriklari\n" +
                          "• Yangi taomlar haqida xabarlar\n" +
                          "• Buyurtma holati haqida bildirishnomalar\n\n" +
                          "Buyruqlar:\n" +
                          "/start - Botni qayta ishga tushirish\n" +
                          "/status - Obuna holatini tekshirish\n" +
                          "/help - Yordam";
                    sendReply(chatId, welcomeMessage);
                } else if (messageText.equals("/status")) {
                    if (onInteraction != null) {
                        onInteraction.accept(chatId);
                    }
                    sendReply(chatId, "✅ Siz obuna bo'lgansiz va xabarlar olishga tayyorsiz!");
                } else if (messageText.equals("/help")) {
                    if (onInteraction != null) {
                        onInteraction.accept(chatId);
                    }
                    String helpMessage =
                        "📚 <b>Bot Yordam</b>\n\n" +
                        "Bu bot orqali siz quyidagilarni olishingiz mumkin:\n\n" +
                        "🎁 <b>Aksiyalar</b> - maxsus takliflar va chegirmalar\n" +
                        "🎂 <b>Tug'ilgan kun</b> - bayram kunida sovg'alar\n" +
                        "🍽 <b>Yangiliklar</b> - yangi taomlar haqida\n" +
                        "📦 <b>Buyurtmalar</b> - buyurtma holati";
                    sendReply(chatId, helpMessage);
                } else {
                    if (onInteraction != null) {
                        onInteraction.accept(chatId);
                    }
                }
            }
        }

        private void sendReply(Long chatId, String text) {
            try {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText(text);
                message.setParseMode("HTML");
                execute(message);
            } catch (TelegramApiException e) {
                // Log error silently
            }
        }
    }
}

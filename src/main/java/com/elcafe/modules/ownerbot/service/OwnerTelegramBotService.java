package com.elcafe.modules.ownerbot.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.ownerbot.config.OwnerBotConfig;
import com.elcafe.modules.ownerbot.entity.OwnerNotificationSettings;
import com.elcafe.modules.ownerbot.entity.OwnerTelegramSubscriber;
import com.elcafe.modules.ownerbot.repository.OwnerNotificationSettingsRepository;
import com.elcafe.modules.ownerbot.repository.OwnerTelegramSubscriberRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Telegram Bot Service for restaurant owners and staff notifications.
 * Handles registration, verification, and real-time notifications.
 */
@Slf4j
@Service
@Lazy
@RequiredArgsConstructor
public class OwnerTelegramBotService {

    private final OwnerBotConfig config;
    private final OwnerTelegramSubscriberRepository subscriberRepository;
    private final OwnerNotificationSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;

    private OwnerBot bot;

    // Cache for pending verifications (code -> user data)
    private final Map<String, PendingVerification> pendingVerifications = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (!config.isEnabled()) {
            log.info("Owner Telegram bot is disabled");
            return;
        }

        if (config.getToken() == null || config.getToken().isEmpty()) {
            log.warn("Owner Telegram bot token is not configured. Bot will not be registered.");
            return;
        }

        try {
            bot = new OwnerBot(config.getToken(), config.getUsername());
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            log.info("Owner Telegram bot registered successfully: @{}", config.getUsername());
        } catch (TelegramApiException e) {
            log.error("Failed to register Owner Telegram bot: {}", e.getMessage());
        }
    }

    public boolean isReady() {
        return config.isEnabled() && bot != null;
    }

    /**
     * Generate a verification code for linking a user account to Telegram
     */
    @Transactional
    public String generateVerificationCode(Long userId, Long restaurantId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));

        String code = generateRandomCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        pendingVerifications.put(code, new PendingVerification(userId, restaurantId, user.getRole().name(), expiresAt));

        return code;
    }

    /**
     * Send a message to a specific chat
     */
    public Integer sendMessage(Long chatId, String message) {
        if (!isReady()) {
            log.debug("Owner bot is disabled or not initialized");
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
            log.error("Failed to send message to chatId {}: {}", chatId, e.getMessage());
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
            log.error("Failed to send message with buttons to chatId {}: {}", chatId, e.getMessage());
            return null;
        }
    }

    private String generateRandomCode() {
        return String.format("%06d", new Random().nextInt(999999));
    }

    /**
     * Inner class for the owner bot implementation
     */
    private class OwnerBot extends TelegramLongPollingBot {
        private final String username;

        public OwnerBot(String token, String username) {
            super(token);
            this.username = username;
        }

        @Override
        public String getBotUsername() {
            return username;
        }

        @Override
        public void onUpdateReceived(Update update) {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update);
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update);
            }
        }

        private void handleTextMessage(Update update) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            org.telegram.telegrambots.meta.api.objects.User telegramUser = update.getMessage().getFrom();

            if (messageText.equals("/start") || messageText.startsWith("/start ")) {
                handleStartCommand(chatId, telegramUser);
            } else if (messageText.equals("/status")) {
                handleStatusCommand(chatId);
            } else if (messageText.equals("/settings")) {
                handleSettingsCommand(chatId);
            } else if (messageText.equals("/help")) {
                handleHelpCommand(chatId);
            } else if (messageText.matches("^\\d{6}$")) {
                // Verification code
                handleVerificationCode(chatId, telegramUser, messageText);
            } else {
                // Update last interaction
                updateLastInteraction(chatId);
            }
        }

        private void handleStartCommand(Long chatId, org.telegram.telegrambots.meta.api.objects.User telegramUser) {
            Optional<OwnerTelegramSubscriber> existingOpt = subscriberRepository.findByTelegramUserId(chatId);

            if (existingOpt.isPresent() && existingOpt.get().getIsVerified()) {
                OwnerTelegramSubscriber subscriber = existingOpt.get();
                String welcomeBack = String.format(
                    "👋 <b>С возвращением, %s!</b>\n\n" +
                    "Вы уже подключены к ресторану <b>%s</b>.\n\n" +
                    "Команды:\n" +
                    "/status - Статус подключения\n" +
                    "/settings - Настройки уведомлений\n" +
                    "/help - Справка",
                    subscriber.getDisplayName(),
                    subscriber.getRestaurant() != null ? subscriber.getRestaurant().getName() : "N/A"
                );
                sendReply(chatId, welcomeBack);
            } else {
                // New user or unverified
                OwnerTelegramSubscriber subscriber = existingOpt.orElse(
                    OwnerTelegramSubscriber.builder()
                        .telegramUserId(chatId)
                        .subscribedAt(LocalDateTime.now())
                        .isActive(true)
                        .isVerified(false)
                        .build()
                );

                subscriber.setUsername(telegramUser.getUserName());
                subscriber.setFirstName(telegramUser.getFirstName());
                subscriber.setLastName(telegramUser.getLastName());
                subscriber.setLanguageCode(telegramUser.getLanguageCode());
                subscriber.setLastInteractionAt(LocalDateTime.now());
                subscriberRepository.save(subscriber);

                String welcomeMessage =
                    "👋 <b>Добро пожаловать в ElCafe Owner Bot!</b>\n\n" +
                    "Этот бот предназначен для владельцев и менеджеров ресторанов.\n\n" +
                    "📱 <b>Вы будете получать уведомления о:</b>\n" +
                    "• Новых заказах\n" +
                    "• Новых бронированиях\n" +
                    "• Низком уровне запасов\n" +
                    "• Отзывах клиентов\n" +
                    "• Ежедневных отчётах\n\n" +
                    "🔐 <b>Для подключения:</b>\n" +
                    "1. Войдите в админ-панель ElCafe\n" +
                    "2. Перейдите в Настройки → Telegram\n" +
                    "3. Получите код подключения\n" +
                    "4. Отправьте код сюда\n\n" +
                    "Или введите 6-значный код подключения:";
                sendReply(chatId, welcomeMessage);
            }
        }

        private void handleVerificationCode(Long chatId, org.telegram.telegrambots.meta.api.objects.User telegramUser, String code) {
            PendingVerification verification = pendingVerifications.remove(code);

            if (verification == null) {
                sendReply(chatId, "❌ Неверный код или код истёк. Получите новый код в админ-панели.");
                return;
            }

            if (verification.expiresAt.isBefore(LocalDateTime.now())) {
                sendReply(chatId, "❌ Код истёк. Получите новый код в админ-панели.");
                return;
            }

            try {
                User user = userRepository.findById(verification.userId).orElse(null);
                Restaurant restaurant = restaurantRepository.findById(verification.restaurantId).orElse(null);

                if (user == null || restaurant == null) {
                    sendReply(chatId, "❌ Ошибка верификации. Попробуйте получить новый код.");
                    return;
                }

                OwnerTelegramSubscriber subscriber = subscriberRepository.findByTelegramUserId(chatId)
                    .orElse(OwnerTelegramSubscriber.builder()
                        .telegramUserId(chatId)
                        .subscribedAt(LocalDateTime.now())
                        .build());

                subscriber.setUsername(telegramUser.getUserName());
                subscriber.setFirstName(telegramUser.getFirstName());
                subscriber.setLastName(telegramUser.getLastName());
                subscriber.setLanguageCode(telegramUser.getLanguageCode());
                subscriber.setUser(user);
                subscriber.setRestaurant(restaurant);
                subscriber.setRole(verification.role);
                subscriber.setIsActive(true);
                subscriber.setIsVerified(true);
                subscriber.setLastInteractionAt(LocalDateTime.now());

                subscriber = subscriberRepository.save(subscriber);

                // Create default notification settings
                OwnerNotificationSettings settings = OwnerNotificationSettings.builder()
                    .subscriber(subscriber)
                    .build();
                settingsRepository.save(settings);

                String fullName = user.getFirstName();
                if (user.getLastName() != null && !user.getLastName().isEmpty()) {
                    fullName += " " + user.getLastName();
                }
                String successMessage = String.format(
                    "✅ <b>Успешно подключено!</b>\n\n" +
                    "👤 Аккаунт: %s\n" +
                    "🏪 Ресторан: %s\n" +
                    "👔 Роль: %s\n\n" +
                    "Теперь вы будете получать уведомления о важных событиях.\n\n" +
                    "Команды:\n" +
                    "/settings - Настройки уведомлений\n" +
                    "/status - Статус подключения",
                    fullName,
                    restaurant.getName(),
                    verification.role
                );
                sendReply(chatId, successMessage);

                log.info("Owner Telegram subscriber verified: chatId={}, userId={}, restaurant={}",
                        chatId, verification.userId, restaurant.getName());

            } catch (Exception e) {
                log.error("Failed to verify subscriber: chatId={}, code={}, error={}",
                        chatId, code, e.getMessage());
                sendReply(chatId, "❌ Ошибка при подключении. Попробуйте позже.");
            }
        }

        private void handleStatusCommand(Long chatId) {
            updateLastInteraction(chatId);

            Optional<OwnerTelegramSubscriber> subscriberOpt = subscriberRepository.findByTelegramUserId(chatId);
            if (subscriberOpt.isEmpty()) {
                sendReply(chatId, "❌ Вы не подключены. Используйте /start для начала.");
                return;
            }

            OwnerTelegramSubscriber subscriber = subscriberOpt.get();
            if (!subscriber.getIsVerified()) {
                sendReply(chatId, "⏳ Ожидает верификации. Введите код подключения из админ-панели.");
                return;
            }

            String status = String.format(
                "📊 <b>Статус подключения</b>\n\n" +
                "✅ Статус: Активен\n" +
                "🏪 Ресторан: %s\n" +
                "👔 Роль: %s\n" +
                "📅 Подключён: %s",
                subscriber.getRestaurant() != null ? subscriber.getRestaurant().getName() : "N/A",
                subscriber.getRole(),
                subscriber.getSubscribedAt() != null ?
                    subscriber.getSubscribedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) : "N/A"
            );
            sendReply(chatId, status);
        }

        private void handleSettingsCommand(Long chatId) {
            updateLastInteraction(chatId);

            Optional<OwnerTelegramSubscriber> subscriberOpt = subscriberRepository.findByTelegramUserId(chatId);
            if (subscriberOpt.isEmpty() || !subscriberOpt.get().getIsVerified()) {
                sendReply(chatId, "❌ Сначала подключитесь к ресторану через /start");
                return;
            }

            OwnerNotificationSettings settings = settingsRepository.findBySubscriberId(subscriberOpt.get().getId())
                .orElse(null);

            if (settings == null) {
                sendReply(chatId, "Настройки не найдены. Используйте админ-панель для настройки.");
                return;
            }

            String settingsText = String.format(
                "⚙️ <b>Настройки уведомлений</b>\n\n" +
                "%s Новые заказы\n" +
                "%s Новые брони\n" +
                "%s Низкий запас\n" +
                "%s Отзывы клиентов\n" +
                "%s Ежедневный отчёт\n" +
                "%s Критические оповещения\n\n" +
                "Для изменения настроек используйте админ-панель.",
                settings.getNotifyNewOrder() ? "✅" : "❌",
                settings.getNotifyNewReservation() ? "✅" : "❌",
                settings.getNotifyLowStock() ? "✅" : "❌",
                settings.getNotifyCustomerReview() ? "✅" : "❌",
                settings.getNotifyDailyReport() ? "✅" : "❌",
                settings.getNotifyCriticalAlerts() ? "✅" : "❌"
            );
            sendReply(chatId, settingsText);
        }

        private void handleHelpCommand(Long chatId) {
            updateLastInteraction(chatId);

            String helpMessage =
                "📚 <b>ElCafe Owner Bot - Справка</b>\n\n" +
                "Этот бот отправляет уведомления владельцам и менеджерам ресторанов.\n\n" +
                "<b>Команды:</b>\n" +
                "/start - Начать/перезапустить бот\n" +
                "/status - Статус подключения\n" +
                "/settings - Настройки уведомлений\n" +
                "/help - Эта справка\n\n" +
                "<b>Типы уведомлений:</b>\n" +
                "🆕 Новые заказы\n" +
                "📅 Новые бронирования\n" +
                "📦 Низкий уровень запасов\n" +
                "⭐ Отзывы клиентов\n" +
                "📊 Ежедневные отчёты\n" +
                "🚨 Критические оповещения\n\n" +
                "Вопросы? Обратитесь в поддержку.";
            sendReply(chatId, helpMessage);
        }

        private void handleCallbackQuery(Update update) {
            // Handle inline button callbacks if needed
            String callbackData = update.getCallbackQuery().getData();
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            // Implement callback handling as needed
        }

        private void updateLastInteraction(Long chatId) {
            subscriberRepository.findByTelegramUserId(chatId).ifPresent(subscriber -> {
                subscriber.setLastInteractionAt(LocalDateTime.now());
                subscriberRepository.save(subscriber);
            });
        }

        private void sendReply(Long chatId, String text) {
            try {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText(text);
                message.setParseMode("HTML");
                execute(message);
            } catch (TelegramApiException e) {
                log.error("Failed to send reply to chatId {}: {}", chatId, e.getMessage());
            }
        }
    }

    private record PendingVerification(Long userId, Long restaurantId, String role, LocalDateTime expiresAt) {}
}

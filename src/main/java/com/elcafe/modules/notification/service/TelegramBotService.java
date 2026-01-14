package com.elcafe.modules.notification.service;

import com.elcafe.modules.notification.config.TelegramConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Telegram Bot Service for sending notifications
 */
@Slf4j
@Service
@Lazy
@RequiredArgsConstructor
public class TelegramBotService {

    private final TelegramConfig telegramConfig;
    private ElCafeBot bot;

    @PostConstruct
    public void init() {
        if (!telegramConfig.isEnabled()) {
            log.info("Telegram bot is disabled");
            return;
        }

        if (telegramConfig.getToken() == null || telegramConfig.getToken().isEmpty()) {
            log.warn("Telegram bot token is not configured. Bot will not be registered.");
            return;
        }

        try {
            bot = new ElCafeBot(telegramConfig.getToken(), telegramConfig.getUsername());
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            log.info("Telegram bot registered successfully: @{}", telegramConfig.getUsername());
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram bot: {}", e.getMessage());
        }
    }

    /**
     * Send a message to a specific chat
     *
     * @param chatId  The chat ID to send the message to
     * @param message The message text
     * @return true if message was sent successfully
     */
    public boolean sendMessage(Long chatId, String message) {
        if (!telegramConfig.isEnabled() || bot == null) {
            log.debug("Telegram bot is disabled or not initialized, skipping message to chatId: {}", chatId);
            return false;
        }

        if (telegramConfig.getToken() == null || telegramConfig.getToken().isEmpty()) {
            log.warn("Telegram bot token is not configured, cannot send message");
            return false;
        }

        try {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId.toString());
            sendMessage.setText(message);
            sendMessage.setParseMode("HTML");
            bot.execute(sendMessage);
            log.debug("Message sent to chatId {}: {}", chatId, message.substring(0, Math.min(50, message.length())));
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message to chatId {}: {}", chatId, e.getMessage());
            return false;
        }
    }

    /**
     * Send a stock alert message
     *
     * @param chatId         The chat ID
     * @param restaurantName Restaurant name
     * @param alertType      Type of alert (LOW_STOCK, REORDER)
     * @param items          List of items with their details
     * @return true if message was sent successfully
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

        return sendMessage(chatId, message);
    }

    /**
     * Inner class for the actual Telegram bot implementation
     */
    private static class ElCafeBot extends TelegramLongPollingBot {
        private final String username;

        public ElCafeBot(String token, String username) {
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
                String messageText = update.getMessage().getText();
                Long chatId = update.getMessage().getChatId();

                // Handle /start command - show chat ID for subscription
                if (messageText.equals("/start")) {
                    String welcomeMessage = String.format(
                        "👋 Добро пожаловать в ElCafe Bot!\n\n" +
                        "🆔 Ваш Chat ID: %d\n\n" +
                        "Используйте этот ID для подписки на уведомления в панели управления.\n\n" +
                        "📦 <b>Уведомления о запасах:</b>\n" +
                        "• Низкий уровень запасов\n" +
                        "• Требуется повторный заказ\n\n" +
                        "📊 <b>Финансовые отчеты:</b>\n" +
                        "• Ежедневная выручка\n" +
                        "• Ежедневные расходы\n" +
                        "• Ежедневная прибыль\n\n" +
                        "Команды:\n" +
                        "/start - Показать Chat ID\n" +
                        "/status - Проверить статус подписки",
                        chatId
                    );
                    sendReply(chatId, welcomeMessage);
                } else if (messageText.equals("/status")) {
                    sendReply(chatId, "✅ Бот активен и готов отправлять уведомления.");
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

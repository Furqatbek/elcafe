package com.elcafe.modules.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared registry for Telegram bot instances.
 * Manages a single TelegramBotsApi instance and prevents duplicate token registration.
 * When two bots (e.g., customer bot and owner bot) are configured with the same token,
 * only the first registration succeeds — the second is skipped with a warning.
 */
@Slf4j
@Component
public class TelegramBotRegistry {

    private TelegramBotsApi botsApi;
    private final Map<String, BotSession> activeSessions = new ConcurrentHashMap<>();

    private synchronized TelegramBotsApi getBotsApi() throws TelegramApiException {
        if (botsApi == null) {
            botsApi = new TelegramBotsApi(DefaultBotSession.class);
        }
        return botsApi;
    }

    /**
     * Register a bot with the shared TelegramBotsApi.
     * If a bot with the same token is already registered, logs a warning and returns null.
     *
     * @param bot the bot to register
     * @return BotSession if registration succeeded, null if the token is already in use
     */
    public synchronized BotSession registerBot(TelegramLongPollingBot bot) {
        String token = bot.getBotToken();
        if (activeSessions.containsKey(token)) {
            log.warn("Telegram bot with this token is already registered (username: @{}). " +
                    "Skipping duplicate registration to avoid 409 Conflict. " +
                    "Ensure each bot uses a unique token.", bot.getBotUsername());
            return null;
        }

        try {
            BotSession session = getBotsApi().registerBot(bot);
            activeSessions.put(token, session);
            return session;
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram bot @{}: {}", bot.getBotUsername(), e.getMessage());
            return null;
        }
    }

    /**
     * Unregister a bot by token, stopping its session.
     *
     * @param token the bot token to unregister
     */
    public synchronized void unregisterBot(String token) {
        BotSession session = activeSessions.remove(token);
        if (session != null && session.isRunning()) {
            session.stop();
        }
    }

    /**
     * Check if a token is currently registered.
     */
    public boolean isRegistered(String token) {
        return token != null && activeSessions.containsKey(token);
    }
}

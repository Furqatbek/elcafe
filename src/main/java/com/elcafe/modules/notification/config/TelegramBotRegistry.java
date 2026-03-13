package com.elcafe.modules.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared registry for Telegram bot instances.
 *
 * Manages a single TelegramBotsApi instance. When multiple bots are registered
 * with the same token (e.g., customer bot and owner bot sharing one token in dev),
 * a single polling session is created and updates are fanned out to every registered
 * handler — so both bots can still process incoming messages independently.
 *
 * The correct production setup is to assign each bot its own unique token.
 */
@Slf4j
@Component
public class TelegramBotRegistry {

    private TelegramBotsApi botsApi;

    /** token -> active BotSession (one per token) */
    private final Map<String, BotSession> activeSessions = new ConcurrentHashMap<>();

    /** token -> all handlers that should receive updates for that token */
    private final Map<String, CopyOnWriteArrayList<TelegramLongPollingBot>> tokenHandlers =
            new ConcurrentHashMap<>();

    private synchronized TelegramBotsApi getBotsApi() throws TelegramApiException {
        if (botsApi == null) {
            botsApi = new TelegramBotsApi(DefaultBotSession.class);
        }
        return botsApi;
    }

    /**
     * Register a bot handler.
     *
     * <ul>
     *   <li>First registration for a token → starts a polling session via a dispatcher bot.</li>
     *   <li>Subsequent registrations with the same token → add the handler to the existing
     *       dispatcher so updates are routed to all handlers. No new session is created.</li>
     * </ul>
     *
     * @return the active BotSession (never null on success)
     */
    public synchronized BotSession registerBot(TelegramLongPollingBot bot) {
        String token = bot.getBotToken();

        CopyOnWriteArrayList<TelegramLongPollingBot> handlers =
                tokenHandlers.computeIfAbsent(token, k -> new CopyOnWriteArrayList<>());
        handlers.add(bot);

        if (activeSessions.containsKey(token)) {
            log.warn("Telegram bot token already has an active session (@{}). " +
                    "Handler added to existing dispatcher — both bots will receive updates. " +
                    "Use separate tokens in production.", bot.getBotUsername());
            return activeSessions.get(token);
        }

        // First bot for this token: start a dispatcher that fans out to all handlers
        DispatcherBot dispatcher = new DispatcherBot(token, bot.getBotUsername(), handlers);
        try {
            BotSession session = getBotsApi().registerBot(dispatcher);
            activeSessions.put(token, session);
            log.debug("Dispatcher started for token with handler @{}", bot.getBotUsername());
            return session;
        } catch (TelegramApiException e) {
            log.error("Failed to start polling for @{}: {}", bot.getBotUsername(), e.getMessage());
            handlers.remove(bot);
            if (handlers.isEmpty()) {
                tokenHandlers.remove(token);
            }
            return null;
        }
    }

    /**
     * Remove a specific handler. If no handlers remain for the token the polling session is stopped.
     */
    public synchronized void unregisterBot(String token, TelegramLongPollingBot bot) {
        CopyOnWriteArrayList<TelegramLongPollingBot> handlers = tokenHandlers.get(token);
        if (handlers != null) {
            handlers.remove(bot);
            if (handlers.isEmpty()) {
                tokenHandlers.remove(token);
                BotSession session = activeSessions.remove(token);
                if (session != null && session.isRunning()) {
                    session.stop();
                }
            }
        }
    }

    /**
     * Returns true when a polling session is active for the given token.
     */
    public boolean isRegistered(String token) {
        return token != null && activeSessions.containsKey(token);
    }

    // -------------------------------------------------------------------------
    // Dispatcher
    // -------------------------------------------------------------------------

    /**
     * A thin TelegramLongPollingBot whose sole job is to fan out every received
     * update to all registered handlers for its token.
     */
    private static class DispatcherBot extends TelegramLongPollingBot {

        private final String username;
        private final List<TelegramLongPollingBot> handlers;

        DispatcherBot(String token, String username, List<TelegramLongPollingBot> handlers) {
            super(token);
            this.username = username;
            this.handlers = handlers;
        }

        @Override
        public String getBotUsername() {
            return username;
        }

        @Override
        public void onUpdateReceived(Update update) {
            for (TelegramLongPollingBot handler : handlers) {
                try {
                    handler.onUpdateReceived(update);
                } catch (Exception e) {
                    log.error("Handler @{} threw an exception processing update: {}",
                            handler.getBotUsername(), e.getMessage());
                }
            }
        }
    }
}

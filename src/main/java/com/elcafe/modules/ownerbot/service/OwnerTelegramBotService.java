package com.elcafe.modules.ownerbot.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.notification.config.TelegramBotRegistry;
import com.elcafe.modules.notification.service.DailyFinancialReportService;
import com.elcafe.modules.ownerbot.entity.OwnerNotificationSettings;
import com.elcafe.modules.ownerbot.entity.OwnerTelegramBotConfig;
import com.elcafe.modules.ownerbot.entity.OwnerTelegramSubscriber;
import com.elcafe.modules.ownerbot.repository.OwnerNotificationSettingsRepository;
import com.elcafe.modules.ownerbot.repository.OwnerTelegramBotConfigRepository;
import com.elcafe.modules.ownerbot.repository.OwnerTelegramSubscriberRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Telegram Bot Service for restaurant owners and staff notifications.
 * Reads configuration from database (managed via frontend admin panel).
 */
@Slf4j
@Service
public class OwnerTelegramBotService {

    private final OwnerTelegramBotConfigRepository configRepository;
    private final OwnerTelegramSubscriberRepository subscriberRepository;
    private final OwnerNotificationSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final TelegramBotRegistry botRegistry;
    private final InventoryIngredientRepository ingredientRepository;
    private final DailyFinancialReportService dailyFinancialReportService;
    private final com.elcafe.modules.waiter.repository.WaiterRepository waiterRepository2;
    private final com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository shiftRepository;
    private final com.elcafe.modules.order.repository.OrderRepository orderRepository;
    private final com.elcafe.modules.financial.service.DashboardService dashboardService;
    private final com.elcafe.modules.pos.shift.repository.EmployeeConsumptionRepository consumptionRepository;

    public OwnerTelegramBotService(
            OwnerTelegramBotConfigRepository configRepository,
            OwnerTelegramSubscriberRepository subscriberRepository,
            OwnerNotificationSettingsRepository settingsRepository,
            UserRepository userRepository,
            RestaurantRepository restaurantRepository,
            TelegramBotRegistry botRegistry,
            InventoryIngredientRepository ingredientRepository,
            @org.springframework.context.annotation.Lazy DailyFinancialReportService dailyFinancialReportService,
            com.elcafe.modules.waiter.repository.WaiterRepository waiterRepository2,
            com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository shiftRepository,
            com.elcafe.modules.order.repository.OrderRepository orderRepository,
            @org.springframework.context.annotation.Lazy com.elcafe.modules.financial.service.DashboardService dashboardService,
            com.elcafe.modules.pos.shift.repository.EmployeeConsumptionRepository consumptionRepository) {
        this.configRepository = configRepository;
        this.subscriberRepository = subscriberRepository;
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.botRegistry = botRegistry;
        this.ingredientRepository = ingredientRepository;
        this.dailyFinancialReportService = dailyFinancialReportService;
        this.waiterRepository2 = waiterRepository2;
        this.shiftRepository = shiftRepository;
        this.orderRepository = orderRepository;
        this.dashboardService = dashboardService;
        this.consumptionRepository = consumptionRepository;
    }

    private OwnerBot bot;
    private BotSession botSession;
    private String currentToken;
    private String welcomeMessage;

    // Cache for pending verifications (code -> user data)
    private final Map<String, PendingVerification> pendingVerifications = new ConcurrentHashMap<>();

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
        Optional<OwnerTelegramBotConfig> configOpt = configRepository.findByIsActiveTrue();

        if (configOpt.isEmpty()) {
            log.info("No active Owner Telegram bot configuration found in database. Bot will not start.");
            log.info("Configure the bot via Settings -> Telegram in the admin panel.");
            return;
        }

        OwnerTelegramBotConfig config = configOpt.get();

        if (config.getBotToken() == null || config.getBotToken().isEmpty()) {
            log.warn("Owner Telegram bot token is empty. Bot will not be registered.");
            return;
        }

        if (!Boolean.TRUE.equals(config.getIsActive())) {
            log.info("Owner Telegram bot is disabled in configuration.");
            return;
        }

        this.currentToken = config.getBotToken();
        this.welcomeMessage = config.getWelcomeMessage();

        bot = new OwnerBot(config.getBotToken(), config.getBotUsername());

        botSession = botRegistry.registerBot(bot);
        if (botSession != null) {
            log.info("Owner Telegram bot registered successfully: @{}", config.getBotUsername());
            registerCommands();
        }
    }

    private void registerCommands() {
        try {
            org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands setCommands =
                    new org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands();
            setCommands.setCommands(List.of(
                    new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("/start", "Начать / Главное меню"),
                    new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("/menu", "Показать кнопки"),
                    new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("/report", "Финансовый отчёт"),
                    new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("/stock", "Проверить запасы"),
                    new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("/sales", "Продажи по сменам"),
                    new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("/status", "Статус подключения"),
                    new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("/settings", "Настройки"),
                    new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("/help", "Справка")
            ));
            bot.execute(setCommands);
            log.info("Owner bot commands registered with Telegram");
        } catch (Exception e) {
            log.warn("Failed to register bot commands: {}", e.getMessage());
        }
    }

    /**
     * Stop the currently running bot.
     */
    public synchronized void stopBot() {
        if (currentToken != null && bot != null) {
            botRegistry.unregisterBot(currentToken, bot);
            log.info("Owner Telegram bot stopped");
        }
        bot = null;
        botSession = null;
        currentToken = null;
    }

    /**
     * Restart the bot with new configuration from database.
     * Called when configuration is updated via frontend.
     */
    public boolean sendStockAlert(Long chatId, String restaurantName, String alertType, String items) {
        String emoji = alertType.equals("LOW_STOCK") ? "🔴" : "🟡";
        String title = alertType.equals("LOW_STOCK") ? "Низкий уровень запасов" : "Требуется заказ";
        String message = String.format(
                "%s <b>%s</b>\n\n🏪 <b>%s</b>\n\n%s\n\n⏰ %s",
                emoji, title, restaurantName, items,
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        return sendMessage(chatId, message) != null;
    }

    public void restartBot() {
        log.info("Restarting Owner Telegram bot with new configuration...");
        initializeBot();
    }

    public boolean isReady() {
        return bot != null && currentToken != null && botRegistry.isRegistered(currentToken);
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
            log.warn("Owner bot is not ready, skipping message to chatId: {}", chatId);
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
            } else if (messageText.equals("/report")) {
                handleReportCommand(chatId);
            } else if (messageText.equals("/stock")) {
                handleStockCommand(chatId);
            } else if (messageText.equals("/menu")) {
                handleMenuCommand(chatId);
            } else if (messageText.equals("/sales")) {
                handleSalesCommand(chatId);
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
                    "Вы подключены к ресторану <b>%s</b>.",
                    subscriber.getDisplayName(),
                    subscriber.getRestaurant() != null ? subscriber.getRestaurant().getName() : "N/A"
                );
                sendMenuButtons(chatId, welcomeBack);
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

                String welcomeMsg = welcomeMessage != null && !welcomeMessage.isEmpty()
                    ? welcomeMessage
                    : "👋 <b>Добро пожаловать в Owner Bot!</b>\n\n" +
                      "Этот бот предназначен для владельцев и менеджеров ресторанов.\n\n" +
                      "📱 <b>Вы будете получать уведомления о:</b>\n" +
                      "• Новых заказах\n" +
                      "• Новых бронированиях\n" +
                      "• Низком уровне запасов\n" +
                      "• Отзывах клиентов\n" +
                      "• Ежедневных отчётах\n\n" +
                      "🔐 <b>Для подключения:</b>\n" +
                      "1. Войдите в админ-панель\n" +
                      "2. Перейдите в Настройки → Telegram\n" +
                      "3. Получите код подключения\n" +
                      "4. Отправьте код сюда\n\n" +
                      "Или введите 6-значный код подключения:";
                sendReply(chatId, welcomeMsg);
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
                    "Теперь вы будете получать уведомления о важных событиях.",
                    fullName,
                    restaurant.getName(),
                    verification.role
                );
                sendMenuButtons(chatId, successMessage);

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
                "📚 <b>Owner Bot - Справка</b>\n\n" +
                "Этот бот отправляет уведомления владельцам и менеджерам ресторанов.\n\n" +
                "<b>Команды:</b>\n" +
                "/start - Начать/перезапустить бот\n" +
                "/menu - Показать кнопки действий\n" +
                "/report - Финансовый отчёт за сегодня\n" +
                "/stock - Проверить низкие запасы\n" +
                "/sales - Продажи по сменам\n" +
                "/status - Статус подключения\n" +
                "/settings - Настройки уведомлений\n" +
                "/help - Эта справка\n\n" +
                "<b>Автоматические уведомления:</b>\n" +
                "🆕 Новые заказы\n" +
                "📅 Новые бронирования\n" +
                "🟢 Сотрудник начал смену\n" +
                "🔴 Сотрудник закончил смену\n" +
                "📦 Низкий уровень запасов\n" +
                "⭐ Отзывы клиентов\n" +
                "📊 Ежедневные отчёты\n" +
                "🚨 Критические оповещения";
            sendReply(chatId, helpMessage);
        }

        private void handleReportCommand(Long chatId) {
            updateLastInteraction(chatId);
            Optional<OwnerTelegramSubscriber> subOpt = subscriberRepository.findByTelegramUserId(chatId);
            if (subOpt.isEmpty() || !subOpt.get().getIsVerified() || subOpt.get().getRestaurant() == null) {
                sendReply(chatId, "❌ Сначала подключитесь к ресторану через /start");
                return;
            }
            try {
                Long restaurantId = subOpt.get().getRestaurant().getId();
                String restaurantName = subOpt.get().getRestaurant().getName();

                // Route through the same DailyFinancialReportService the
                // scheduled FinancialAlert report uses so all three Telegram
                // surfaces (scheduled, shift-closed, in-chat /report) show
                // identical numbers and the same drawer/other/payroll split.
                com.elcafe.modules.notification.service.DailyFinancialReportService.DailyMetrics m =
                        dailyFinancialReportService.calculateDailyMetrics(
                                restaurantId, java.time.LocalDate.now());

                StringBuilder sb = new StringBuilder();
                sb.append("📊 <b>Финансовый отчёт за сегодня</b>\n\n");
                sb.append(String.format("🏪 <b>%s</b>\n", restaurantName));
                sb.append(String.format("📅 %s\n\n",
                        java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))));

                sb.append(String.format("💰 <b>Выручка:</b> %,.2f\n", m.totalRevenue()));
                sb.append(String.format("📦 Заказов: %d\n\n", m.orderCount()));

                // Revenue breakdown (mirrors the scheduled report's structure).
                if (m.salesRevenue() != null) {
                    sb.append("<b>Детализация выручки:</b>\n");
                    sb.append(String.format("   🍽 Продажи: %,.2f\n", m.salesRevenue()));
                    if (m.serviceFeeRevenue() != null && m.serviceFeeRevenue().signum() > 0)
                        sb.append(String.format("   🔧 Сервисный сбор: %,.2f\n", m.serviceFeeRevenue()));
                    if (m.deliveryFeeRevenue() != null && m.deliveryFeeRevenue().signum() > 0)
                        sb.append(String.format("   🚗 Доставка: %,.2f\n", m.deliveryFeeRevenue()));
                    if (m.tipRevenue() != null && m.tipRevenue().signum() > 0)
                        sb.append(String.format("   💵 Чаевые: %,.2f\n", m.tipRevenue()));
                    sb.append("\n");
                }

                // Expenses with drawer/other split — same shape as the
                // scheduled daily report so the operator's mental model
                // never has to switch between the two surfaces.
                sb.append(String.format("💸 <b>Расходы:</b> %,.2f\n", m.totalExpenses()));
                if (m.shiftDrawerExpenses() != null && m.shiftDrawerExpenses().signum() > 0)
                    sb.append(String.format("   🪙 Из кассы смены: %,.2f\n", m.shiftDrawerExpenses()));
                if (m.otherExpenses() != null && m.otherExpenses().signum() > 0)
                    sb.append(String.format("   🏦 Прочие: %,.2f\n", m.otherExpenses()));
                if (m.totalPayroll() != null && m.totalPayroll().signum() > 0)
                    sb.append(String.format("   👥 Зарплата: %,.2f\n", m.totalPayroll()));

                // Net profit
                String profitEmoji = m.netIncome().compareTo(java.math.BigDecimal.ZERO) >= 0 ? "📈" : "📉";
                sb.append(String.format("\n%s <b>Чистая прибыль:</b> %,.2f", profitEmoji, m.netIncome()));

                if (m.totalRevenue().signum() > 0) {
                    java.math.BigDecimal margin = m.netIncome()
                            .divide(m.totalRevenue(), 4, java.math.RoundingMode.HALF_UP)
                            .multiply(new java.math.BigDecimal("100"));
                    sb.append(String.format("\n📊 Маржа: %.1f%%", margin));
                }

                sendReply(chatId, sb.toString());
            } catch (Exception e) {
                log.error("Failed to send report via bot command: {}", e.getMessage());
                sendReply(chatId, "❌ Ошибка при формировании отчёта: " + e.getMessage());
            }
        }

        private void handleStockCommand(Long chatId) {
            updateLastInteraction(chatId);
            Optional<OwnerTelegramSubscriber> subOpt = subscriberRepository.findByTelegramUserId(chatId);
            if (subOpt.isEmpty() || !subOpt.get().getIsVerified() || subOpt.get().getRestaurant() == null) {
                sendReply(chatId, "❌ Сначала подключитесь к ресторану через /start");
                return;
            }
            try {
                Long restaurantId = subOpt.get().getRestaurant().getId();
                List<Ingredient> lowStockItems = ingredientRepository.findLowStockIngredients(restaurantId);

                List<String[]> belowThreshold = new java.util.ArrayList<>();
                for (Ingredient ingredient : lowStockItems) {
                    java.math.BigDecimal threshold = ingredient.getMinimumStock() != null
                            ? ingredient.getMinimumStock() : java.math.BigDecimal.TEN;
                    if (ingredient.getCurrentStock().compareTo(threshold) < 0) {
                        belowThreshold.add(new String[]{
                                ingredient.getName(),
                                String.valueOf(ingredient.getCurrentStock().intValue()),
                                String.valueOf(threshold.intValue())
                        });
                    }
                }

                if (belowThreshold.isEmpty()) {
                    sendReply(chatId, "✅ Все запасы в норме! Нет товаров с низким уровнем.");
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("📦 <b>Низкий уровень запасов (%d)</b>\n\n", belowThreshold.size()));
                    for (String[] item : belowThreshold) {
                        sb.append(String.format("⚠️ <b>%s</b>: %s (мин: %s)\n", item[0], item[1], item[2]));
                    }
                    sb.append("\n⚠️ Рекомендуется пополнить запасы");
                    sendReply(chatId, sb.toString());
                }
            } catch (Exception e) {
                log.error("Failed to check stock via bot command: {}", e.getMessage());
                sendReply(chatId, "❌ Ошибка при проверке запасов: " + e.getMessage());
            }
        }

        private void handleMenuCommand(Long chatId) {
            updateLastInteraction(chatId);
            Optional<OwnerTelegramSubscriber> subOpt = subscriberRepository.findByTelegramUserId(chatId);
            if (subOpt.isEmpty() || !subOpt.get().getIsVerified()) {
                sendReply(chatId, "❌ Сначала подключитесь к ресторану через /start");
                return;
            }
            sendMenuButtons(chatId, "📋 <b>Выберите действие:</b>");
        }

        private void sendMenuButtons(Long chatId, String text) {
            try {
                SendMessage msg = new SendMessage();
                msg.setChatId(chatId.toString());
                msg.setText(text);
                msg.setParseMode("HTML");

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

                InlineKeyboardButton reportBtn = new InlineKeyboardButton();
                reportBtn.setText("📊 Финансовый отчёт");
                reportBtn.setCallbackData("cmd_report");

                InlineKeyboardButton stockBtn = new InlineKeyboardButton();
                stockBtn.setText("📦 Проверить запасы");
                stockBtn.setCallbackData("cmd_stock");

                keyboard.add(List.of(reportBtn, stockBtn));

                InlineKeyboardButton salesBtn = new InlineKeyboardButton();
                salesBtn.setText("🧾 Продажи по сменам");
                salesBtn.setCallbackData("cmd_sales");

                InlineKeyboardButton statusBtn = new InlineKeyboardButton();
                statusBtn.setText("ℹ️ Статус");
                statusBtn.setCallbackData("cmd_status");

                keyboard.add(List.of(salesBtn, statusBtn));

                markup.setKeyboard(keyboard);
                msg.setReplyMarkup(markup);
                execute(msg);
            } catch (TelegramApiException e) {
                log.error("Failed to send menu to chatId {}: {}", chatId, e.getMessage());
            }
        }

        private void handleCallbackQuery(Update update) {
            String callbackData = update.getCallbackQuery().getData();
            Long chatId = update.getCallbackQuery().getMessage().getChatId();

            try {
                org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answer =
                        new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
                answer.setCallbackQueryId(update.getCallbackQuery().getId());
                execute(answer);
            } catch (TelegramApiException e) {
                log.warn("Failed to answer callback query: {}", e.getMessage());
            }

            if (callbackData.startsWith("sales_waiter_")) {
                handleSalesWaiterSelected(chatId, callbackData);
            } else if (callbackData.startsWith("sales_shift_")) {
                handleSalesShiftSelected(chatId, callbackData);
            } else if (callbackData.startsWith("sales_wp_")) {
                handleSalesCommandPage(chatId, Integer.parseInt(callbackData.replace("sales_wp_", "")));
            } else if (callbackData.startsWith("sales_sp_")) {
                String[] parts = callbackData.replace("sales_sp_", "").split("_");
                handleSalesWaiterPage(chatId, parts[0], Integer.parseInt(parts[1]));
            } else {
                switch (callbackData) {
                    case "cmd_report" -> handleReportCommand(chatId);
                    case "cmd_stock" -> handleStockCommand(chatId);
                    case "cmd_status" -> handleStatusCommand(chatId);
                    case "cmd_settings" -> handleSettingsCommand(chatId);
                    case "cmd_sales" -> handleSalesCommand(chatId);
                    default -> sendReply(chatId, "Неизвестная команда");
                }
            }
        }

        private static final int PAGE_SIZE = 5;

        private void handleSalesCommand(Long chatId) {
            handleSalesCommandPage(chatId, 0);
        }

        private void handleSalesCommandPage(Long chatId, int page) {
            updateLastInteraction(chatId);
            Optional<OwnerTelegramSubscriber> subOpt = subscriberRepository.findByTelegramUserId(chatId);
            if (subOpt.isEmpty() || !subOpt.get().getIsVerified() || subOpt.get().getRestaurant() == null) {
                sendReply(chatId, "❌ Сначала подключитесь к ресторану через /start");
                return;
            }
            try {
                List<com.elcafe.modules.waiter.entity.Waiter> waiters =
                        waiterRepository2.findByActiveTrueOrderByNameAsc();

                if (waiters.isEmpty()) {
                    sendReply(chatId, "Нет активных официантов");
                    return;
                }

                int totalPages = (waiters.size() + PAGE_SIZE - 1) / PAGE_SIZE;
                int fromIdx = page * PAGE_SIZE;
                int toIdx = Math.min(fromIdx + PAGE_SIZE, waiters.size());
                List<com.elcafe.modules.waiter.entity.Waiter> pageItems = waiters.subList(fromIdx, toIdx);

                SendMessage msg = new SendMessage();
                msg.setChatId(chatId.toString());
                msg.setText(String.format("🧾 <b>Продажи по сменам</b>\n\nВыберите официанта (%d/%d):", page + 1, totalPages));
                msg.setParseMode("HTML");

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                for (com.elcafe.modules.waiter.entity.Waiter w : pageItems) {
                    InlineKeyboardButton btn = new InlineKeyboardButton();
                    btn.setText("👤 " + w.getName());
                    btn.setCallbackData("sales_waiter_" + w.getId());
                    keyboard.add(List.of(btn));
                }

                // Pagination buttons
                List<InlineKeyboardButton> navRow = new ArrayList<>();
                if (page > 0) {
                    InlineKeyboardButton prev = new InlineKeyboardButton();
                    prev.setText("◀️ Назад");
                    prev.setCallbackData("sales_wp_" + (page - 1));
                    navRow.add(prev);
                }
                if (page < totalPages - 1) {
                    InlineKeyboardButton next = new InlineKeyboardButton();
                    next.setText("Вперёд ▶️");
                    next.setCallbackData("sales_wp_" + (page + 1));
                    navRow.add(next);
                }
                if (!navRow.isEmpty()) keyboard.add(navRow);

                markup.setKeyboard(keyboard);
                msg.setReplyMarkup(markup);
                execute(msg);
            } catch (Exception e) {
                log.error("Failed to handle /sales: {}", e.getMessage());
                sendReply(chatId, "❌ Ошибка: " + e.getMessage());
            }
        }

        private void handleSalesWaiterSelected(Long chatId, String callbackData) {
            handleSalesWaiterPage(chatId, callbackData.replace("sales_waiter_", ""), 0);
        }

        private void handleSalesWaiterPage(Long chatId, String waiterIdStr, int page) {
            try {
                Long waiterId = Long.parseLong(waiterIdStr);
                com.elcafe.modules.waiter.entity.Waiter waiter = waiterRepository2.findById(waiterId).orElse(null);
                if (waiter == null) { sendReply(chatId, "Официант не найден"); return; }

                List<com.elcafe.modules.pos.shift.entity.EmployeeShift> shifts =
                        shiftRepository.findByWaiterIdOrderByDateDesc(waiterId);

                if (shifts.isEmpty()) {
                    sendReply(chatId, "У " + waiter.getName() + " нет смен");
                    return;
                }

                int totalPages = (shifts.size() + PAGE_SIZE - 1) / PAGE_SIZE;
                int fromIdx = page * PAGE_SIZE;
                int toIdx = Math.min(fromIdx + PAGE_SIZE, shifts.size());
                List<com.elcafe.modules.pos.shift.entity.EmployeeShift> pageItems = shifts.subList(fromIdx, toIdx);

                SendMessage msg = new SendMessage();
                msg.setChatId(chatId.toString());
                msg.setText(String.format("👤 <b>%s</b>\n\nВыберите смену (%d/%d):", waiter.getName(), page + 1, totalPages));
                msg.setParseMode("HTML");

                java.time.ZoneId zone = java.time.ZoneId.systemDefault();
                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm");
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                for (var s : pageItems) {
                    String clockIn = s.getClockIn() != null ? s.getClockIn().atZoneSameInstant(zone).format(fmt) : "--";
                    String clockOut = s.getClockOut() != null ? s.getClockOut().atZoneSameInstant(zone).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : "...";
                    String label = clockIn + "—" + clockOut + " | " + (s.getTotalOrders() != null ? s.getTotalOrders() : 0) + " зак.";
                    InlineKeyboardButton btn = new InlineKeyboardButton();
                    btn.setText(label);
                    btn.setCallbackData("sales_shift_" + s.getId());
                    keyboard.add(List.of(btn));
                }

                // Pagination buttons
                List<InlineKeyboardButton> navRow = new ArrayList<>();
                if (page > 0) {
                    InlineKeyboardButton prev = new InlineKeyboardButton();
                    prev.setText("◀️ Назад");
                    prev.setCallbackData("sales_sp_" + waiterId + "_" + (page - 1));
                    navRow.add(prev);
                }
                if (page < totalPages - 1) {
                    InlineKeyboardButton next = new InlineKeyboardButton();
                    next.setText("Вперёд ▶️");
                    next.setCallbackData("sales_sp_" + waiterId + "_" + (page + 1));
                    navRow.add(next);
                }
                if (!navRow.isEmpty()) keyboard.add(navRow);

                markup.setKeyboard(keyboard);
                msg.setReplyMarkup(markup);
                execute(msg);
            } catch (Exception e) {
                log.error("Failed to load waiter shifts: {}", e.getMessage());
                sendReply(chatId, "❌ Ошибка: " + e.getMessage());
            }
        }

        private void handleSalesShiftSelected(Long chatId, String callbackData) {
            try {
                Long shiftId = Long.parseLong(callbackData.replace("sales_shift_", ""));
                com.elcafe.modules.pos.shift.entity.EmployeeShift shift = shiftRepository.findById(shiftId).orElse(null);
                if (shift == null) { sendReply(chatId, "Смена не найдена"); return; }

                List<com.elcafe.modules.order.entity.Order> orders = orderRepository.findByShiftIdWithItems(shiftId);

                String waiterName = shift.getWaiter() != null ? shift.getWaiter().getName()
                        : (shift.getEmployee() != null ? shift.getEmployee().getFullName() : "—");
                java.time.ZoneId zone = java.time.ZoneId.systemDefault();
                String clockIn = shift.getClockIn() != null
                        ? shift.getClockIn().atZoneSameInstant(zone).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) : "--";
                String clockOut = shift.getClockOut() != null
                        ? shift.getClockOut().atZoneSameInstant(zone).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : "...";

                // Aggregate by payment method
                Map<String, java.math.BigDecimal> revenueByMethod = new java.util.LinkedHashMap<>();
                Map<String, Map<String, int[]>> itemsByMethod = new java.util.LinkedHashMap<>();
                java.math.BigDecimal totalRevenue = java.math.BigDecimal.ZERO;
                int totalOrderCount = 0;

                for (var order : orders) {
                    String method = "Другое";
                    if (order.getPayment() != null && order.getPayment().getMethod() != null) {
                        method = switch (order.getPayment().getMethod().name()) {
                            case "CASH" -> "Наличные";
                            case "CARD" -> "Карта";
                            case "MOBILE_PAYMENT" -> "Мобильный";
                            default -> order.getPayment().getMethod().name();
                        };
                    } else if (order.getPayments() != null && !order.getPayments().isEmpty()
                            && order.getPayments().get(0).getMethod() != null) {
                        method = switch (order.getPayments().get(0).getMethod().name()) {
                            case "CASH" -> "Наличные";
                            case "CARD" -> "Карта";
                            case "MOBILE_PAYMENT" -> "Мобильный";
                            default -> order.getPayments().get(0).getMethod().name();
                        };
                    }

                    java.math.BigDecimal orderTotal = order.getTotal() != null ? order.getTotal() : java.math.BigDecimal.ZERO;
                    revenueByMethod.merge(method, orderTotal, java.math.BigDecimal::add);
                    totalRevenue = totalRevenue.add(orderTotal);
                    totalOrderCount++;

                    Map<String, int[]> methodItems = itemsByMethod.computeIfAbsent(method, k -> new java.util.LinkedHashMap<>());
                    for (var item : order.getItems()) {
                        if (item.isDeleted() || Boolean.TRUE.equals(item.getIsPackagingItem())) continue;
                        String key = item.getProductName() + (item.getVariantName() != null ? " (" + item.getVariantName() + ")" : "");
                        int[] data = methodItems.computeIfAbsent(key, k -> new int[]{0, 0});
                        data[0] += item.getQuantity();
                        data[1] += item.getTotalPrice() != null ? item.getTotalPrice().intValue() : 0;
                    }
                }

                StringBuilder sb = new StringBuilder();
                sb.append(String.format("🧾 <b>Продажи за смену</b>\n\n👤 %s\n⏰ %s — %s\n📦 Заказов: %d\n",
                        waiterName, clockIn, clockOut, totalOrderCount));

                if (itemsByMethod.isEmpty()) {
                    sb.append("\nНет проданных товаров");
                } else {
                    for (var methodEntry : itemsByMethod.entrySet()) {
                        String method = methodEntry.getKey();
                        String emoji = method.equals("Наличные") ? "💵" : method.equals("Карта") ? "💳" : "💰";
                        java.math.BigDecimal methodTotal = revenueByMethod.getOrDefault(method, java.math.BigDecimal.ZERO);

                        sb.append(String.format("\n%s <b>%s: %,.2f</b>\n", emoji, method, methodTotal));
                        int i = 1;
                        for (var itemEntry : methodEntry.getValue().entrySet()) {
                            sb.append(String.format("  %d. %s × %d = %,d\n", i++, itemEntry.getKey(), itemEntry.getValue()[0], itemEntry.getValue()[1]));
                        }
                    }
                    sb.append(String.format("\n💰 <b>Общий итого: %,.2f</b>", totalRevenue));
                }

                // Employee consumption section
                var consumptions = consumptionRepository.findByEmployeeShift_IdOrderByConsumedAtDesc(shiftId);
                if (!consumptions.isEmpty()) {
                    java.math.BigDecimal consumptionTotal = java.math.BigDecimal.ZERO;
                    sb.append("\n\n🍽 <b>Потребление сотрудника:</b>\n");
                    for (var c : consumptions) {
                        java.math.BigDecimal price = c.getProduct() != null && c.getProduct().getPrice() != null
                                ? c.getProduct().getPrice().multiply(java.math.BigDecimal.valueOf(c.getQuantity()))
                                : c.getTotalCost();
                        sb.append(String.format("  • %s × %d = %,.2f\n", c.getProductName(), c.getQuantity(), price));
                        consumptionTotal = consumptionTotal.add(price);
                    }
                    sb.append(String.format("  <b>Итого потребление: %,.2f</b>", consumptionTotal));
                }

                sendReply(chatId, sb.toString());
            } catch (Exception e) {
                log.error("Failed to load shift sales: {}", e.getMessage());
                sendReply(chatId, "❌ Ошибка: " + e.getMessage());
            }
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

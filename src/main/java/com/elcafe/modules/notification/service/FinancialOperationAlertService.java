package com.elcafe.modules.notification.service;

import com.elcafe.modules.notification.config.FinancialAlertConfig;
import com.elcafe.modules.notification.entity.FinancialAlertSubscription;
import com.elcafe.modules.notification.repository.FinancialAlertSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for sending alerts on financial operation failures.
 * Sends Telegram notifications when critical financial operations fail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialOperationAlertService {

    private final TelegramBotService telegramBotService;
    private final FinancialAlertSubscriptionRepository subscriptionRepository;
    private final FinancialAlertConfig alertConfig;

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    /**
     * Alert on revenue recording failure.
     */
    @Async
    public void alertRevenueRecordingFailure(Long orderId, String orderNumber,
                                              Long restaurantId, BigDecimal amount,
                                              String errorMessage, int attemptNumber) {
        if (!alertConfig.isEnabled()) {
            return;
        }

        String message = formatRevenueFailureAlert(orderId, orderNumber, amount,
                                                    errorMessage, attemptNumber);
        sendAlertToRestaurantAdmins(restaurantId, message);
    }

    /**
     * Alert on payment processing failure.
     */
    @Async
    public void alertPaymentFailure(Long orderId, String orderNumber,
                                     Long restaurantId, BigDecimal amount,
                                     String paymentMethod, String errorMessage) {
        if (!alertConfig.isEnabled()) {
            return;
        }

        String message = formatPaymentFailureAlert(orderId, orderNumber, amount,
                                                   paymentMethod, errorMessage);
        sendAlertToRestaurantAdmins(restaurantId, message);
    }

    /**
     * Alert on refund processing failure.
     */
    @Async
    public void alertRefundFailure(Long orderId, String orderNumber,
                                    Long restaurantId, BigDecimal amount,
                                    String errorMessage) {
        if (!alertConfig.isEnabled()) {
            return;
        }

        String message = formatRefundFailureAlert(orderId, orderNumber, amount, errorMessage);
        sendAlertToRestaurantAdmins(restaurantId, message);
    }

    /**
     * Alert on concurrent modification (OptimisticLockException).
     */
    @Async
    public void alertConcurrentModification(String entityType, Long entityId,
                                             Long restaurantId, String operation) {
        if (!alertConfig.isEnabled()) {
            return;
        }

        String message = formatConcurrentModificationAlert(entityType, entityId, operation);
        sendAlertToRestaurantAdmins(restaurantId, message);
    }

    private void sendAlertToRestaurantAdmins(Long restaurantId, String message) {
        try {
            List<FinancialAlertSubscription> subscriptions =
                    subscriptionRepository.findByRestaurant_IdAndActiveTrue(restaurantId);

            for (FinancialAlertSubscription subscription : subscriptions) {
                try {
                    telegramBotService.sendMessage(restaurantId, subscription.getTelegramChatId(), message);
                } catch (Exception e) {
                    log.error("Failed to send alert to chatId {}: {}",
                              subscription.getTelegramChatId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send financial operation alert for restaurant {}: {}",
                      restaurantId, e.getMessage());
        }
    }

    private String formatRevenueFailureAlert(Long orderId, String orderNumber,
                                              BigDecimal amount, String errorMessage,
                                              int attemptNumber) {
        return String.format("""
            ⚠️ <b>ВНИМАНИЕ: Ошибка записи выручки</b>

            📋 Заказ: #%s (ID: %d)
            💰 Сумма: %,.2f
            🔄 Попытка: %d

            ❌ Ошибка: %s

            ⏰ %s

            <i>Требуется проверка финансовых записей</i>
            """,
                orderNumber != null ? orderNumber : "N/A",
                orderId,
                amount != null ? amount : BigDecimal.ZERO,
                attemptNumber,
                errorMessage,
                LocalDateTime.now().format(DATETIME_FORMATTER));
    }

    private String formatPaymentFailureAlert(Long orderId, String orderNumber,
                                              BigDecimal amount, String paymentMethod,
                                              String errorMessage) {
        return String.format("""
            🚨 <b>ОШИБКА ПЛАТЕЖА</b>

            📋 Заказ: #%s (ID: %d)
            💳 Способ оплаты: %s
            💰 Сумма: %,.2f

            ❌ Ошибка: %s

            ⏰ %s
            """,
                orderNumber != null ? orderNumber : "N/A",
                orderId,
                paymentMethod,
                amount != null ? amount : BigDecimal.ZERO,
                errorMessage,
                LocalDateTime.now().format(DATETIME_FORMATTER));
    }

    private String formatRefundFailureAlert(Long orderId, String orderNumber,
                                             BigDecimal amount, String errorMessage) {
        return String.format("""
            🚨 <b>ОШИБКА ВОЗВРАТА</b>

            📋 Заказ: #%s (ID: %d)
            💰 Сумма возврата: %,.2f

            ❌ Ошибка: %s

            ⏰ %s

            <i>Требуется ручная проверка</i>
            """,
                orderNumber != null ? orderNumber : "N/A",
                orderId,
                amount != null ? amount : BigDecimal.ZERO,
                errorMessage,
                LocalDateTime.now().format(DATETIME_FORMATTER));
    }

    private String formatConcurrentModificationAlert(String entityType, Long entityId,
                                                      String operation) {
        return String.format("""
            ⚠️ <b>Конфликт параллельных операций</b>

            📝 Тип: %s
            🔢 ID: %d
            🔧 Операция: %s

            <i>Данные были изменены другой транзакцией.
            Пожалуйста, повторите операцию.</i>

            ⏰ %s
            """,
                entityType,
                entityId,
                operation,
                LocalDateTime.now().format(DATETIME_FORMATTER));
    }
}

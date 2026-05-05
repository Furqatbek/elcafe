package com.elcafe.modules.ownerbot.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.ownerbot.entity.OwnerNotificationLog;
import com.elcafe.modules.ownerbot.entity.OwnerNotificationSettings;
import com.elcafe.modules.ownerbot.entity.OwnerTelegramSubscriber;
import com.elcafe.modules.ownerbot.enums.OwnerNotificationType;
import com.elcafe.modules.ownerbot.repository.OwnerNotificationLogRepository;
import com.elcafe.modules.ownerbot.repository.OwnerTelegramSubscriberRepository;
import com.elcafe.modules.reservation.entity.Reservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Service for sending notifications to restaurant owners and staff via Telegram.
 */
@Slf4j
@Service
public class OwnerNotificationService {

    private final OwnerTelegramBotService botService;
    private final OwnerTelegramSubscriberRepository subscriberRepository;
    private final OwnerNotificationLogRepository logRepository;

    public OwnerNotificationService(
            @org.springframework.context.annotation.Lazy OwnerTelegramBotService botService,
            OwnerTelegramSubscriberRepository subscriberRepository,
            OwnerNotificationLogRepository logRepository) {
        this.botService = botService;
        this.subscriberRepository = subscriberRepository;
        this.logRepository = logRepository;
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getInstance(new Locale("uz", "UZ"));

    /**
     * Send notification about a new order
     */
    @Async
    @Transactional
    public void notifyNewOrder(Order order) {
        if (order == null || order.getRestaurant() == null) {
            return;
        }

        Long restaurantId = order.getRestaurant().getId();
        List<OwnerTelegramSubscriber> subscribers = getEligibleSubscribers(restaurantId, OwnerNotificationType.NEW_ORDER);

        String message = formatNewOrderMessage(order);

        for (OwnerTelegramSubscriber subscriber : subscribers) {
            OwnerNotificationSettings settings = subscriber.getNotificationSettings();

            // Check minimum order amount threshold
            if (settings != null && settings.getMinOrderAmountNotify() != null) {
                if (order.getTotal().compareTo(settings.getMinOrderAmountNotify()) < 0) {
                    continue;
                }
            }

            sendNotification(subscriber, OwnerNotificationType.NEW_ORDER, message, "ORDER", order.getId());
        }

        log.info("New order notification sent for order #{} to {} subscribers",
                order.getOrderNumber(), subscribers.size());
    }

    /**
     * Send notification about a new reservation
     */
    @Async
    @Transactional
    public void notifyNewReservation(Reservation reservation) {
        if (reservation == null || reservation.getRestaurant() == null) {
            return;
        }

        Long restaurantId = reservation.getRestaurant().getId();
        List<OwnerTelegramSubscriber> subscribers = getEligibleSubscribers(restaurantId, OwnerNotificationType.NEW_RESERVATION);

        String message = formatNewReservationMessage(reservation);

        for (OwnerTelegramSubscriber subscriber : subscribers) {
            sendNotification(subscriber, OwnerNotificationType.NEW_RESERVATION, message, "RESERVATION", reservation.getId());
        }

        log.info("New reservation notification sent for {} to {} subscribers",
                reservation.getConfirmationCode(), subscribers.size());
    }

    /**
     * Send notification about cancelled order
     */
    @Async
    @Transactional
    public void notifyOrderCancelled(Order order, String reason) {
        if (order == null || order.getRestaurant() == null) {
            return;
        }

        Long restaurantId = order.getRestaurant().getId();
        List<OwnerTelegramSubscriber> subscribers = getEligibleSubscribers(restaurantId, OwnerNotificationType.ORDER_CANCELLED);

        String message = formatOrderCancelledMessage(order, reason);

        for (OwnerTelegramSubscriber subscriber : subscribers) {
            sendNotification(subscriber, OwnerNotificationType.ORDER_CANCELLED, message, "ORDER", order.getId());
        }
    }

    /**
     * Send notification about cancelled reservation
     */
    @Async
    @Transactional
    public void notifyReservationCancelled(Reservation reservation, String reason) {
        if (reservation == null || reservation.getRestaurant() == null) {
            return;
        }

        Long restaurantId = reservation.getRestaurant().getId();
        List<OwnerTelegramSubscriber> subscribers = getEligibleSubscribers(restaurantId, OwnerNotificationType.RESERVATION_CANCELLED);

        String message = formatReservationCancelledMessage(reservation, reason);

        for (OwnerTelegramSubscriber subscriber : subscribers) {
            sendNotification(subscriber, OwnerNotificationType.RESERVATION_CANCELLED, message, "RESERVATION", reservation.getId());
        }
    }

    /**
     * Send low stock alert
     */
    @Async
    @Transactional
    public void notifyLowStock(Long restaurantId, String productName, int currentStock, int threshold) {
        List<OwnerTelegramSubscriber> subscribers = getEligibleSubscribers(restaurantId, OwnerNotificationType.LOW_STOCK);

        String message = String.format(
            "📦 <b>Низкий уровень запасов</b>\n\n" +
            "Товар: <b>%s</b>\n" +
            "Текущий остаток: <b>%d</b>\n" +
            "Минимум: %d\n\n" +
            "⚠️ Рекомендуется пополнить запасы",
            productName, currentStock, threshold
        );

        for (OwnerTelegramSubscriber subscriber : subscribers) {
            sendNotification(subscriber, OwnerNotificationType.LOW_STOCK, message, "INVENTORY", null);
        }
    }

    /**
     * Send a single batched low-stock alert covering all affected items for one restaurant.
     * Callers should prefer this over calling notifyLowStock() in a loop to avoid
     * submitting one async task per item and saturating the executor queue.
     */
    @Async
    @Transactional
    public void notifyLowStockBatch(Long restaurantId, List<String[]> items) {
        // items: each element is [name, currentStock, threshold]
        if (items == null || items.isEmpty()) return;

        List<OwnerTelegramSubscriber> subscribers = getEligibleSubscribers(restaurantId, OwnerNotificationType.LOW_STOCK);
        if (subscribers.isEmpty()) return;

        StringBuilder sb = new StringBuilder("📦 <b>Низкий уровень запасов</b>\n\n");
        for (String[] item : items) {
            sb.append(String.format("• <b>%s</b> — остаток: %s (мин: %s)\n", item[0], item[1], item[2]));
        }
        sb.append("\n⚠️ Рекомендуется пополнить запасы");
        String message = sb.toString();

        for (OwnerTelegramSubscriber subscriber : subscribers) {
            sendNotification(subscriber, OwnerNotificationType.LOW_STOCK, message, "INVENTORY", null);
        }
    }

    /**
     * Send customer review notification
     */
    @Async
    @Transactional
    public void notifyCustomerReview(Long restaurantId, String customerName, int rating, String comment, Long reviewId) {
        List<OwnerTelegramSubscriber> subscribers = getEligibleSubscribers(restaurantId, OwnerNotificationType.CUSTOMER_REVIEW);

        String stars = "⭐".repeat(rating) + "☆".repeat(5 - rating);
        String message = String.format(
            "⭐ <b>Новый отзыв</b>\n\n" +
            "Клиент: %s\n" +
            "Оценка: %s (%d/5)\n\n" +
            "💬 <i>%s</i>\n\n" +
            "📅 %s",
            customerName != null ? customerName : "Аноним",
            stars, rating,
            comment != null && !comment.isEmpty() ? comment : "Без комментария",
            LocalDateTime.now().format(DATETIME_FORMAT)
        );

        for (OwnerTelegramSubscriber subscriber : subscribers) {
            sendNotification(subscriber, OwnerNotificationType.CUSTOMER_REVIEW, message, "REVIEW", reviewId);
        }
    }

    /**
     * Send daily sales report
     */
    @Async
    @Transactional
    public void sendDailySalesReport(Long restaurantId, String restaurantName,
                                      int totalOrders, BigDecimal totalRevenue,
                                      int reservations, BigDecimal avgOrderValue) {
        List<OwnerTelegramSubscriber> subscribers = subscriberRepository
                .findActiveSubscribersWithSettings(restaurantId);

        String message = String.format(
            "📊 <b>Ежедневный отчёт</b>\n" +
            "🏪 %s\n" +
            "📅 %s\n\n" +
            "═══════════════════\n\n" +
            "📦 <b>Заказы:</b> %d\n" +
            "💰 <b>Выручка:</b> %s UZS\n" +
            "📈 <b>Средний чек:</b> %s UZS\n" +
            "📅 <b>Бронирований:</b> %d\n\n" +
            "═══════════════════\n\n" +
            "Хорошего вечера! 🌙",
            restaurantName,
            LocalDateTime.now().format(DATE_FORMAT),
            totalOrders,
            CURRENCY_FORMAT.format(totalRevenue),
            CURRENCY_FORMAT.format(avgOrderValue),
            reservations
        );

        for (OwnerTelegramSubscriber subscriber : subscribers) {
            if (subscriber.canReceiveNotification("DAILY_REPORT")) {
                OwnerNotificationSettings settings = subscriber.getNotificationSettings();
                if (settings != null && !settings.getReceiveDailySummary()) {
                    continue;
                }
                sendNotification(subscriber, OwnerNotificationType.DAILY_REPORT, message, null, null);
            }
        }

        log.info("Daily report sent for restaurant {} to subscribers", restaurantName);
    }

    /**
     * Send critical system alert
     */
    @Async
    @Transactional
    public void notifyShiftOpened(Long restaurantId, String employeeName, String clockInTime) {
        List<OwnerTelegramSubscriber> subscribers = getEligibleSubscribers(restaurantId, OwnerNotificationType.SHIFT_OPENED);

        String message = String.format(
            "🟢 <b>Смена открыта</b>\n\n" +
            "👤 <b>%s</b>\n" +
            "⏰ Начало: %s\n\n" +
            "📍 Ресторан ID: %d",
            employeeName, clockInTime, restaurantId
        );

        for (OwnerTelegramSubscriber subscriber : subscribers) {
            sendNotification(subscriber, OwnerNotificationType.SHIFT_OPENED, message, "SHIFT", null);
        }

        log.info("Shift opened notification sent for {} at restaurant {}", employeeName, restaurantId);
    }

    @Async
    @Transactional
    public void notifyShiftClosed(Long restaurantId, String employeeName, String clockInTime,
                                   String clockOutTime, long workedMinutes, int orderCount,
                                   java.math.BigDecimal totalSales, java.math.BigDecimal cashVariance) {
        List<OwnerTelegramSubscriber> subscribers = getEligibleSubscribers(restaurantId, OwnerNotificationType.SHIFT_CLOSED);

        long hours = workedMinutes / 60;
        long mins = workedMinutes % 60;
        String varianceText = cashVariance != null && cashVariance.compareTo(java.math.BigDecimal.ZERO) != 0
            ? String.format("\n💰 Расхождение: %s", cashVariance)
            : "\n💰 Расхождение: 0 ✓";

        String message = String.format(
            "🔴 <b>Смена закрыта</b>\n\n" +
            "👤 <b>%s</b>\n" +
            "⏰ %s — %s (%dч %dмин)\n" +
            "📦 Заказов: %d\n" +
            "💵 Выручка: %s%s",
            employeeName, clockInTime, clockOutTime, hours, mins,
            orderCount, totalSales != null ? totalSales : "0",
            varianceText
        );

        for (OwnerTelegramSubscriber subscriber : subscribers) {
            sendNotification(subscriber, OwnerNotificationType.SHIFT_CLOSED, message, "SHIFT", null);
        }

        log.info("Shift closed notification sent for {} at restaurant {}", employeeName, restaurantId);
    }

    @Async
    @Transactional
    public void sendCriticalAlert(Long restaurantId, String title, String details) {
        List<OwnerTelegramSubscriber> subscribers = getEligibleSubscribers(restaurantId, OwnerNotificationType.CRITICAL_ALERT);

        String message = String.format(
            "🚨 <b>КРИТИЧЕСКОЕ ОПОВЕЩЕНИЕ</b>\n\n" +
            "<b>%s</b>\n\n" +
            "%s\n\n" +
            "⏰ %s",
            title,
            details,
            LocalDateTime.now().format(DATETIME_FORMAT)
        );

        for (OwnerTelegramSubscriber subscriber : subscribers) {
            // Critical alerts bypass quiet hours
            sendNotificationDirect(subscriber, OwnerNotificationType.CRITICAL_ALERT, message, null, null);
        }

        log.warn("Critical alert sent for restaurant {}: {}", restaurantId, title);
    }

    // ============ Private Helper Methods ============

    private List<OwnerTelegramSubscriber> getEligibleSubscribers(Long restaurantId, OwnerNotificationType type) {
        return subscriberRepository.findActiveSubscribersWithSettings(restaurantId).stream()
                .filter(s -> s.canReceiveNotification(type.name()))
                .filter(s -> {
                    OwnerNotificationSettings settings = s.getNotificationSettings();
                    return settings == null || !settings.isInQuietHours();
                })
                .toList();
    }

    private void sendNotification(OwnerTelegramSubscriber subscriber, OwnerNotificationType type,
                                  String message, String entityType, Long entityId) {
        OwnerNotificationSettings settings = subscriber.getNotificationSettings();

        // Check quiet hours
        if (settings != null && settings.isInQuietHours()) {
            log.debug("Skipping notification to {} due to quiet hours", subscriber.getTelegramUserId());
            return;
        }

        sendNotificationDirect(subscriber, type, message, entityType, entityId);
    }

    private void sendNotificationDirect(OwnerTelegramSubscriber subscriber, OwnerNotificationType type,
                                        String message, String entityType, Long entityId) {
        OwnerNotificationLog logEntry = OwnerNotificationLog.builder()
                .subscriber(subscriber)
                .telegramUserId(subscriber.getTelegramUserId())
                .notificationType(type)
                .title(type.getTitleRu())
                .message(message)
                .relatedEntityType(entityType)
                .relatedEntityId(entityId)
                .status("PENDING")
                .build();

        try {
            Integer messageId = botService.sendMessage(subscriber.getTelegramUserId(), message);
            if (messageId != null) {
                logEntry.markSent(messageId);
            } else {
                logEntry.markFailed("Bot not ready or failed to send");
            }
        } catch (Exception e) {
            logEntry.markFailed(e.getMessage());
            log.error("Failed to send {} notification to {}: {}",
                    type, subscriber.getTelegramUserId(), e.getMessage());
        }

        logRepository.save(logEntry);
    }

    private String formatNewOrderMessage(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("🆕 <b>Новый заказ!</b>\n\n");
        sb.append(String.format("📦 Заказ: <b>#%s</b>\n", order.getOrderNumber()));

        if (order.getOrderType() != null) {
            String orderTypeText = switch (order.getOrderType().name()) {
                case "DINE_IN" -> "🍽 В зале";
                case "TAKEAWAY" -> "🥡 С собой";
                case "DELIVERY" -> "🚗 Доставка";
                default -> order.getOrderType().name();
            };
            sb.append(String.format("Тип: %s\n", orderTypeText));
        }

        if (order.getDiningTable() != null) {
            sb.append(String.format("🪑 Столик: %s\n", order.getDiningTable().getTableNumber()));
        }

        sb.append(String.format("💰 Сумма: <b>%s UZS</b>\n", CURRENCY_FORMAT.format(order.getTotal())));

        if (order.getCustomer() != null) {
            String customerName = order.getCustomer().getFirstName();
            if (order.getCustomer().getLastName() != null) {
                customerName += " " + order.getCustomer().getLastName();
            }
            sb.append(String.format("👤 Клиент: %s\n", customerName));
        }

        sb.append(String.format("\n⏰ %s", LocalDateTime.now().format(TIME_FORMAT)));

        return sb.toString();
    }

    private String formatNewReservationMessage(Reservation reservation) {
        StringBuilder sb = new StringBuilder();
        sb.append("📅 <b>Новая бронь!</b>\n\n");
        sb.append(String.format("🔑 Код: <b>%s</b>\n", reservation.getConfirmationCode()));
        sb.append(String.format("📆 Дата: %s\n", reservation.getReservationDate().format(DATE_FORMAT)));
        sb.append(String.format("⏰ Время: %s\n", reservation.getReservationTime().format(TIME_FORMAT)));
        sb.append(String.format("👥 Гостей: %d\n", reservation.getPartySize()));

        if (reservation.getTable() != null) {
            sb.append(String.format("🪑 Столик: #%s\n", reservation.getTable().getTableNumber()));
        }

        sb.append(String.format("👤 Имя: %s\n", reservation.getCustomerName()));
        sb.append(String.format("📱 Тел: %s\n", reservation.getCustomerPhone()));

        if (reservation.getSpecialRequests() != null && !reservation.getSpecialRequests().isEmpty()) {
            sb.append(String.format("\n💬 Пожелания: <i>%s</i>", reservation.getSpecialRequests()));
        }

        return sb.toString();
    }

    private String formatOrderCancelledMessage(Order order, String reason) {
        return String.format(
            "❌ <b>Заказ отменён</b>\n\n" +
            "📦 Заказ: #%s\n" +
            "💰 Сумма: %s UZS\n" +
            "📝 Причина: %s\n\n" +
            "⏰ %s",
            order.getOrderNumber(),
            CURRENCY_FORMAT.format(order.getTotal()),
            reason != null ? reason : "Не указана",
            LocalDateTime.now().format(TIME_FORMAT)
        );
    }

    private String formatReservationCancelledMessage(Reservation reservation, String reason) {
        return String.format(
            "❌ <b>Бронь отменена</b>\n\n" +
            "🔑 Код: %s\n" +
            "📆 Была на: %s %s\n" +
            "👥 Гостей: %d\n" +
            "👤 Клиент: %s\n" +
            "📝 Причина: %s\n\n" +
            "⏰ %s",
            reservation.getConfirmationCode(),
            reservation.getReservationDate().format(DATE_FORMAT),
            reservation.getReservationTime().format(TIME_FORMAT),
            reservation.getPartySize(),
            reservation.getCustomerName(),
            reason != null ? reason : "Не указана",
            LocalDateTime.now().format(TIME_FORMAT)
        );
    }
}

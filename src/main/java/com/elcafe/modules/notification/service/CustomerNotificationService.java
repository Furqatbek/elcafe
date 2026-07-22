package com.elcafe.modules.notification.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.reservation.entity.Reservation;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import com.elcafe.modules.telegram.repository.TelegramSubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Service for sending notifications to customers via Telegram.
 * Handles order status updates, reservation confirmations, and reminders.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerNotificationService {

    private final TelegramBotService telegramBotService;
    private final TelegramSubscriberRepository subscriberRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Send order status update notification to customer
     */
    @Async
    public void notifyOrderStatusUpdate(Order order, OrderStatus newStatus) {
        if (order == null || order.getCustomer() == null) {
            return;
        }

        Long customerId = order.getCustomer().getId();
        Optional<TelegramSubscriber> subscriberOpt = subscriberRepository.findByCustomerId(customerId);

        if (subscriberOpt.isEmpty() || !subscriberOpt.get().getIsActive()) {
            log.debug("Customer {} is not subscribed to Telegram notifications", customerId);
            return;
        }

        TelegramSubscriber subscriber = subscriberOpt.get();
        String message = formatOrderStatusMessage(order, newStatus);

        try {
            telegramBotService.sendMessage(subscriber.getTelegramUserId(), message);
            log.info("Order status notification sent to customer {} for order #{}",
                    customerId, order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to send order status notification to customer {}: {}",
                    customerId, e.getMessage());
        }
    }

    /**
     * Send reservation confirmation notification to customer
     */
    @Async
    public void notifyReservationConfirmed(Reservation reservation) {
        if (reservation == null) {
            return;
        }

        TelegramSubscriber subscriber = findSubscriberForReservation(reservation);
        if (subscriber == null) {
            return;
        }

        String message = formatReservationConfirmedMessage(reservation);

        try {
            telegramBotService.sendMessage(subscriber.getTelegramUserId(), message);
            log.info("Reservation confirmation sent for code {}", reservation.getConfirmationCode());
        } catch (Exception e) {
            log.error("Failed to send reservation confirmation: {}", e.getMessage());
        }
    }

    /**
     * Send reservation reminder notification to customer
     */
    @Async
    public void notifyReservationReminder(Reservation reservation) {
        if (reservation == null) {
            return;
        }

        TelegramSubscriber subscriber = findSubscriberForReservation(reservation);
        if (subscriber == null) {
            return;
        }

        String message = formatReservationReminderMessage(reservation);

        try {
            telegramBotService.sendMessage(subscriber.getTelegramUserId(), message);
            log.info("Reservation reminder sent for code {}", reservation.getConfirmationCode());
        } catch (Exception e) {
            log.error("Failed to send reservation reminder: {}", e.getMessage());
        }
    }

    /**
     * Send reservation cancellation notification to customer
     */
    @Async
    public void notifyReservationCancelled(Reservation reservation, String reason) {
        if (reservation == null) {
            return;
        }

        TelegramSubscriber subscriber = findSubscriberForReservation(reservation);
        if (subscriber == null) {
            return;
        }

        String message = formatReservationCancelledMessage(reservation, reason);

        try {
            telegramBotService.sendMessage(subscriber.getTelegramUserId(), message);
            log.info("Reservation cancellation sent for code {}", reservation.getConfirmationCode());
        } catch (Exception e) {
            log.error("Failed to send reservation cancellation: {}", e.getMessage());
        }
    }

    // ============ Private Helper Methods ============

    private TelegramSubscriber findSubscriberForReservation(Reservation reservation) {
        // First try to find by customer ID
        if (reservation.getCustomer() != null) {
            Optional<TelegramSubscriber> subscriberOpt =
                    subscriberRepository.findByCustomerId(reservation.getCustomer().getId());
            if (subscriberOpt.isPresent() && subscriberOpt.get().getIsActive()) {
                return subscriberOpt.get();
            }
        }
        return null;
    }

    private String formatOrderStatusMessage(Order order, OrderStatus status) {
        String statusEmoji = getStatusEmoji(status);
        String statusText = getStatusText(status);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s <b>Buyurtma holati yangilandi</b>\n\n", statusEmoji));
        sb.append(String.format("📦 Buyurtma: <b>#%s</b>\n", order.getOrderNumber()));
        sb.append(String.format("📊 Holat: <b>%s</b>\n", statusText));

        // Add additional info based on status
        switch (status) {
            case ACCEPTED -> sb.append("\n✅ Sizning buyurtmangiz qabul qilindi va tayyorlanmoqda!");
            case PREPARING -> sb.append("\n👨‍🍳 Oshpazlar buyurtmangizni tayyorlayapti!");
            case READY -> sb.append("\n🔔 Buyurtmangiz tayyor! Olib ketishingiz mumkin.");
            case ON_DELIVERY -> sb.append("\n🚗 Buyurtmangiz yo'lda! Tez orada yetkaziladi.");
            case DELIVERED -> sb.append("\n✅ Buyurtmangiz yetkazildi! Yoqimli ishtaha!");
            case COMPLETED -> sb.append("\n🎉 Buyurtma yakunlandi! Rahmat, yana kutamiz!");
            case CANCELLED -> sb.append("\n❌ Buyurtma bekor qilindi. Savollar bo'lsa, biz bilan bog'laning.");
            default -> {}
        }

        return sb.toString();
    }

    private String formatReservationConfirmedMessage(Reservation reservation) {
        return String.format(
            "✅ <b>Buyurtma tasdiqlandi!</b>\n\n" +
            "🔑 Kod: <b>%s</b>\n" +
            "📅 Sana: %s\n" +
            "⏰ Vaqt: %s\n" +
            "👥 Mehmonlar: %d kishi\n" +
            "🏪 %s\n\n" +
            "📍 Manzil: %s\n\n" +
            "⚠️ Iltimos, belgilangan vaqtdan 10 daqiqa oldin keling.\n" +
            "Bekor qilish uchun qo'ng'iroq qiling.",
            reservation.getConfirmationCode(),
            reservation.getReservationDate().format(DATE_FORMAT),
            reservation.getReservationTime().format(TIME_FORMAT),
            reservation.getPartySize(),
            reservation.getRestaurant().getName(),
            reservation.getRestaurant().getAddress() != null ?
                reservation.getRestaurant().getAddress() : "Ma'lumot yo'q"
        );
    }

    private String formatReservationReminderMessage(Reservation reservation) {
        return String.format(
            "🔔 <b>Eslatma: Bugun sizning buyurtmangiz bor!</b>\n\n" +
            "🔑 Kod: <b>%s</b>\n" +
            "⏰ Vaqt: %s\n" +
            "👥 Mehmonlar: %d kishi\n" +
            "🏪 %s\n\n" +
            "Sizni kutamiz! 😊",
            reservation.getConfirmationCode(),
            reservation.getReservationTime().format(TIME_FORMAT),
            reservation.getPartySize(),
            reservation.getRestaurant().getName()
        );
    }

    private String formatReservationCancelledMessage(Reservation reservation, String reason) {
        return String.format(
            "❌ <b>Buyurtma bekor qilindi</b>\n\n" +
            "🔑 Kod: %s\n" +
            "📅 Sana edi: %s %s\n" +
            "📝 Sabab: %s\n\n" +
            "Savollar bo'lsa, biz bilan bog'laning.",
            reservation.getConfirmationCode(),
            reservation.getReservationDate().format(DATE_FORMAT),
            reservation.getReservationTime().format(TIME_FORMAT),
            reason != null ? reason : "Ko'rsatilmagan"
        );
    }

    private String getStatusEmoji(OrderStatus status) {
        return switch (status) {
            case NEW, PENDING, PLACED -> "📝";
            case ACCEPTED -> "✅";
            case PREPARING -> "👨‍🍳";
            case READY -> "🔔";
            case PICKED_UP -> "📦";
            case COURIER_ASSIGNED -> "🚗";
            case ON_DELIVERY -> "🛵";
            case DELIVERED -> "✅";
            case COMPLETED -> "🎉";
            case CANCELLED, REJECTED -> "❌";
        };
    }

    private String getStatusText(OrderStatus status) {
        return switch (status) {
            case NEW, PENDING -> "Yangi";
            case PLACED -> "Joylashtirildi";
            case ACCEPTED -> "Qabul qilindi";
            case PREPARING -> "Tayyorlanmoqda";
            case READY -> "Tayyor";
            case PICKED_UP -> "Olib ketildi";
            case COURIER_ASSIGNED -> "Kuryer tayinlandi";
            case ON_DELIVERY -> "Yetkazilmoqda";
            case DELIVERED -> "Yetkazildi";
            case COMPLETED -> "Yakunlandi";
            case CANCELLED -> "Bekor qilindi";
            case REJECTED -> "Rad etildi";
        };
    }
}

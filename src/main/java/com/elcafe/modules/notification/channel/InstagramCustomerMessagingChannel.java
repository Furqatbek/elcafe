package com.elcafe.modules.notification.channel;

import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.enums.InstagramMessageType;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import com.elcafe.modules.instagram.service.InstagramApiClient;
import com.elcafe.modules.instagram.service.InstagramMessageLogger;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.reservation.entity.Reservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

/**
 * Instagram implementation of {@link CustomerMessagingChannel} — the DM counterpart to
 * {@link TelegramCustomerMessagingChannel}. Every message body below is the Telegram copy with the
 * {@code <b>...</b>} tags stripped and nothing else changed: Instagram DMs render no markup, so the same
 * emoji, line breaks and Uzbek wording carry over as plain text.
 *
 * <p>A customer can be linked to more than one {@link InstagramSubscriber} — e.g. they messaged more
 * than one restaurant's Instagram account and both got linked to the same {@code customer} record — so
 * every active, linked subscriber is notified independently, each through its <em>own</em> restaurant's
 * active bot config, never the order/reservation's restaurant. This mirrors
 * {@link TelegramCustomerMessagingChannel}, which always sends "from the bot of the restaurant this
 * person actually subscribed to" (V164) rather than the restaurant that owns the order/reservation.
 *
 * <p>Best-effort throughout, matching {@link InstagramMessageLogger}'s own contract: a missing
 * subscriber, an inactive link, a missing bot config, or an exception anywhere in the lookup/format/send
 * path is logged and skipped rather than thrown, and one subscriber's failure never stops the others —
 * so Instagram trouble can never take Telegram (or a future channel) down with it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstagramCustomerMessagingChannel implements CustomerMessagingChannel {

    private final InstagramSubscriberRepository subscriberRepository;
    private final InstagramBotConfigRepository configRepository;
    private final InstagramApiClient apiClient;
    private final InstagramMessageLogger messageLogger;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public void notifyOrderStatus(Order order, OrderStatus newStatus) {
        if (order == null || order.getCustomer() == null) {
            return;
        }
        notifyLinkedSubscribers(order.getCustomer().getId(),
                () -> formatOrderStatusMessage(order, newStatus),
                "order status update", "order #" + order.getOrderNumber());
    }

    @Override
    public void notifyReservationConfirmed(Reservation reservation) {
        if (reservation == null || reservation.getCustomer() == null) {
            return;
        }
        notifyLinkedSubscribers(reservation.getCustomer().getId(),
                () -> formatReservationConfirmedMessage(reservation),
                "reservation confirmation", "code " + reservation.getConfirmationCode());
    }

    @Override
    public void notifyReservationReminder(Reservation reservation) {
        if (reservation == null || reservation.getCustomer() == null) {
            return;
        }
        notifyLinkedSubscribers(reservation.getCustomer().getId(),
                () -> formatReservationReminderMessage(reservation),
                "reservation reminder", "code " + reservation.getConfirmationCode());
    }

    @Override
    public void notifyReservationCancelled(Reservation reservation, String reason) {
        if (reservation == null || reservation.getCustomer() == null) {
            return;
        }
        notifyLinkedSubscribers(reservation.getCustomer().getId(),
                () -> formatReservationCancelledMessage(reservation, reason),
                "reservation cancellation", "code " + reservation.getConfirmationCode());
    }

    // ============ Private Helper Methods ============

    /**
     * Resolve every Instagram subscriber linked to {@code customerId} and send {@code messageSupplier}'s
     * text to each active one, through its own restaurant's bot config. Never throws: the subscriber
     * lookup, message formatting, and each per-subscriber send are all guarded so a problem anywhere
     * degrades to a logged skip instead of propagating to the orchestrator.
     */
    private void notifyLinkedSubscribers(Long customerId, Supplier<String> messageSupplier,
                                          String what, String ref) {
        List<InstagramSubscriber> subscribers;
        try {
            subscribers = subscriberRepository.findByCustomerId(customerId);
        } catch (Exception e) {
            log.error("Failed to look up Instagram subscribers for customer {} while sending {}: {}",
                    customerId, what, e.getMessage());
            return;
        }
        if (subscribers.isEmpty()) {
            log.debug("Customer {} has no linked Instagram subscriber — skipping {}", customerId, what);
            return;
        }

        String text;
        try {
            text = messageSupplier.get();
        } catch (Exception e) {
            log.error("Failed to format Instagram {} message for customer {} ({}): {}",
                    what, customerId, ref, e.getMessage());
            return;
        }

        for (InstagramSubscriber subscriber : subscribers) {
            try {
                sendToSubscriber(subscriber, text, what, ref);
            } catch (Exception e) {
                log.error("Failed to send Instagram {} to customer {} (subscriber {}, {}): {}",
                        what, customerId, subscriber.getId(), ref, e.getMessage());
            }
        }
    }

    /** Send to one subscriber, skipping (with a debug log) if it is inactive or its restaurant has no active bot config. */
    private void sendToSubscriber(InstagramSubscriber subscriber, String text, String what, String ref) {
        if (!Boolean.TRUE.equals(subscriber.getIsActive())) {
            log.debug("Instagram subscriber {} is not active — skipping {} ({})",
                    subscriber.getId(), what, ref);
            return;
        }
        InstagramBotConfig config = configRepository
                .findByRestaurantIdAndIsActiveTrue(subscriber.getRestaurantId())
                .orElse(null);
        if (config == null) {
            log.debug("No active Instagram config for restaurant {} — skipping {} to subscriber {} ({})",
                    subscriber.getRestaurantId(), what, subscriber.getId(), ref);
            return;
        }

        InstagramSendResult result = apiClient.sendMessage(config, subscriber.getIgsid(), text);
        messageLogger.record(config, subscriber.getIgsid(), subscriber,
                InstagramMessageType.NOTIFICATION, text, result, null);

        if (result.delivered()) {
            log.info("Instagram {} sent to subscriber {} ({})", what, subscriber.getId(), ref);
        } else {
            log.warn("Instagram {} to subscriber {} ({}) failed: {}",
                    what, subscriber.getId(), ref, result.message());
        }
    }

    private String formatOrderStatusMessage(Order order, OrderStatus status) {
        String statusEmoji = getStatusEmoji(status);
        String statusText = getStatusText(status);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s Buyurtma holati yangilandi\n\n", statusEmoji));
        sb.append(String.format("📦 Buyurtma: #%s\n", order.getOrderNumber()));
        sb.append(String.format("📊 Holat: %s\n", statusText));

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
            "✅ Buyurtma tasdiqlandi!\n\n" +
            "🔑 Kod: %s\n" +
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
            "🔔 Eslatma: Bugun sizning buyurtmangiz bor!\n\n" +
            "🔑 Kod: %s\n" +
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
            "❌ Buyurtma bekor qilindi\n\n" +
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

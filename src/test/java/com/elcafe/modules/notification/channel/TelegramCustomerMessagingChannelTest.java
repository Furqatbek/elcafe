package com.elcafe.modules.notification.channel;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.notification.service.TelegramBotService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.reservation.entity.Reservation;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import com.elcafe.modules.telegram.repository.TelegramSubscriberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link TelegramCustomerMessagingChannel}'s exact message output. When
 * {@code CustomerNotificationService} was split into a channel-neutral orchestrator plus one
 * {@code @Component} per channel, the Telegram formatting code was meant to move verbatim — same HTML
 * tags, same emoji, same Uzbek copy. These tests pin the literal output so an accidental wording or
 * markup change during any future edit of this class fails loudly instead of silently reaching
 * customers.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramCustomerMessagingChannelTest {

    private static final Long RESTAURANT_ID = 7L;
    private static final Long CUSTOMER_ID = 42L;
    private static final Long TELEGRAM_USER_ID = 555L;

    @Mock private TelegramBotService telegramBotService;
    @Mock private TelegramSubscriberRepository subscriberRepository;

    @InjectMocks private TelegramCustomerMessagingChannel channel;

    private TelegramSubscriber activeSubscriber() {
        return TelegramSubscriber.builder()
                .id(1L)
                .restaurantId(RESTAURANT_ID)
                .telegramUserId(TELEGRAM_USER_ID)
                .isActive(true)
                .build();
    }

    private Customer customer() {
        Customer c = new Customer();
        c.setId(CUSTOMER_ID);
        return c;
    }

    private Restaurant restaurant() {
        Restaurant r = new Restaurant();
        r.setId(RESTAURANT_ID);
        r.setName("Qahvoon Chilonzor");
        r.setAddress("Chilonzor 5");
        return r;
    }

    // ---------------------------------------------------------------- order status (pinned)

    @Test
    @DisplayName("notifyOrderStatus(READY) sends the exact pinned HTML message, from the subscriber's own restaurant bot")
    void notifyOrderStatus_ready_pinnedOutput() {
        when(subscriberRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(activeSubscriber()));

        Order order = Order.builder().orderNumber("ORD-100").customer(customer()).build();

        channel.notifyOrderStatus(order, OrderStatus.READY);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramBotService).sendMessage(eq(RESTAURANT_ID), eq(TELEGRAM_USER_ID), messageCaptor.capture());

        String expected = "🔔 <b>Buyurtma holati yangilandi</b>\n\n"
                + "📦 Buyurtma: <b>#ORD-100</b>\n"
                + "📊 Holat: <b>Tayyor</b>\n"
                + "\n🔔 Buyurtmangiz tayyor! Olib ketishingiz mumkin.";
        assertThat(messageCaptor.getValue()).isEqualTo(expected);
    }

    // ---------------------------------------------------------------- reservation confirmed (pinned)

    @Test
    @DisplayName("notifyReservationConfirmed sends the exact pinned HTML message")
    void notifyReservationConfirmed_pinnedOutput() {
        when(subscriberRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(activeSubscriber()));

        Reservation reservation = Reservation.builder()
                .customer(customer())
                .restaurant(restaurant())
                .confirmationCode("ABC123")
                .reservationDate(LocalDate.of(2026, 8, 1))
                .reservationTime(LocalTime.of(19, 30))
                .partySize(4)
                .build();

        channel.notifyReservationConfirmed(reservation);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramBotService).sendMessage(eq(RESTAURANT_ID), eq(TELEGRAM_USER_ID), messageCaptor.capture());

        String expected = "✅ <b>Buyurtma tasdiqlandi!</b>\n\n"
                + "🔑 Kod: <b>ABC123</b>\n"
                + "📅 Sana: 01.08.2026\n"
                + "⏰ Vaqt: 19:30\n"
                + "👥 Mehmonlar: 4 kishi\n"
                + "🏪 Qahvoon Chilonzor\n\n"
                + "📍 Manzil: Chilonzor 5\n\n"
                + "⚠️ Iltimos, belgilangan vaqtdan 10 daqiqa oldin keling.\n"
                + "Bekor qilish uchun qo'ng'iroq qiling.";
        assertThat(messageCaptor.getValue()).isEqualTo(expected);
    }

    // ---------------------------------------------------------------- gates preserved by the move

    @Test
    @DisplayName("no Telegram subscriber for the customer -> no send")
    void notifyOrderStatus_noSubscriber_skipsSend() {
        when(subscriberRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
        Order order = Order.builder().orderNumber("ORD-1").customer(customer()).build();

        channel.notifyOrderStatus(order, OrderStatus.READY);

        verify(telegramBotService, never()).sendMessage(any(), any(), any());
    }

    @Test
    @DisplayName("inactive Telegram subscriber -> no send")
    void notifyOrderStatus_inactiveSubscriber_skipsSend() {
        TelegramSubscriber inactive = activeSubscriber();
        inactive.setIsActive(false);
        when(subscriberRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(inactive));
        Order order = Order.builder().orderNumber("ORD-1").customer(customer()).build();

        channel.notifyOrderStatus(order, OrderStatus.READY);

        verify(telegramBotService, never()).sendMessage(any(), any(), any());
    }
}

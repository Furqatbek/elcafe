package com.elcafe.modules.notification.channel;

import com.elcafe.modules.customer.entity.Customer;
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
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@link InstagramCustomerMessagingChannel}: plain-text delivery to a customer's linked,
 * active Instagram subscriber(s), each through their own restaurant's active bot config, logged as
 * {@link InstagramMessageType#NOTIFICATION}; and the skip gates that keep it silent (never throwing)
 * when there is nothing to send to.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramCustomerMessagingChannelTest {

    private static final Long RESTAURANT_ID = 9L;
    private static final Long CUSTOMER_ID = 42L;

    @Mock private InstagramSubscriberRepository subscriberRepository;
    @Mock private InstagramBotConfigRepository configRepository;
    @Mock private InstagramApiClient apiClient;
    @Mock private InstagramMessageLogger messageLogger;

    @InjectMocks private InstagramCustomerMessagingChannel channel;

    private InstagramBotConfig activeConfig;

    @BeforeEach
    void setUp() {
        activeConfig = InstagramBotConfig.builder().id(1L).restaurantId(RESTAURANT_ID).isActive(true).build();
    }

    private InstagramSubscriber activeSubscriber() {
        return InstagramSubscriber.builder()
                .id(5L)
                .restaurantId(RESTAURANT_ID)
                .igsid("igsid-1")
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

    // ---------------------------------------------------------------- happy path

    @Test
    @DisplayName("active subscriber + active config -> plain-text DM sent via the subscriber's own restaurant config, logged as NOTIFICATION")
    void notifyOrderStatus_activeSubscriber_sendsPlainTextAndLogs() {
        InstagramSubscriber subscriber = activeSubscriber();
        when(subscriberRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of(subscriber));
        when(configRepository.findByRestaurantIdAndIsActiveTrue(RESTAURANT_ID)).thenReturn(Optional.of(activeConfig));
        when(apiClient.sendMessage(eq(activeConfig), eq("igsid-1"), anyString())).thenReturn(InstagramSendResult.ok());

        Order order = Order.builder().orderNumber("ORD-100").customer(customer()).build();

        channel.notifyOrderStatus(order, OrderStatus.READY);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).sendMessage(eq(activeConfig), eq("igsid-1"), textCaptor.capture());
        String text = textCaptor.getValue();

        assertThat(text).doesNotContain("<b>").doesNotContain("</b>");
        assertThat(text).contains("🔔").contains("Buyurtma holati yangilandi").contains("#ORD-100").contains("Tayyor");

        verify(messageLogger).record(eq(activeConfig), eq("igsid-1"), eq(subscriber),
                eq(InstagramMessageType.NOTIFICATION), eq(text), any(InstagramSendResult.class), isNull());
    }

    @Test
    @DisplayName("notifyReservationConfirmed sends the exact pinned plain-text message (Telegram copy, HTML stripped)")
    void notifyReservationConfirmed_sendsPlainText() {
        InstagramSubscriber subscriber = activeSubscriber();
        when(subscriberRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of(subscriber));
        when(configRepository.findByRestaurantIdAndIsActiveTrue(RESTAURANT_ID)).thenReturn(Optional.of(activeConfig));
        when(apiClient.sendMessage(eq(activeConfig), eq("igsid-1"), anyString())).thenReturn(InstagramSendResult.ok());

        Reservation reservation = Reservation.builder()
                .customer(customer())
                .restaurant(restaurant())
                .confirmationCode("ABC123")
                .reservationDate(LocalDate.of(2026, 8, 1))
                .reservationTime(LocalTime.of(19, 30))
                .partySize(4)
                .build();

        channel.notifyReservationConfirmed(reservation);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).sendMessage(eq(activeConfig), eq("igsid-1"), textCaptor.capture());

        String expected =
                "✅ Buyurtma tasdiqlandi!\n\n"
                + "🔑 Kod: ABC123\n"
                + "📅 Sana: 01.08.2026\n"
                + "⏰ Vaqt: 19:30\n"
                + "👥 Mehmonlar: 4 kishi\n"
                + "🏪 Qahvoon Chilonzor\n\n"
                + "📍 Manzil: Chilonzor 5\n\n"
                + "⚠️ Iltimos, belgilangan vaqtdan 10 daqiqa oldin keling.\n"
                + "Bekor qilish uchun qo'ng'iroq qiling.";
        assertThat(textCaptor.getValue()).isEqualTo(expected);
    }

    // ---------------------------------------------------------------- skip gates

    @Test
    @DisplayName("no linked Instagram subscriber -> no send, no config lookup, no log")
    void notifyOrderStatus_noSubscriber_skipsSendAndLog() {
        when(subscriberRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of());
        Order order = Order.builder().orderNumber("ORD-1").customer(customer()).build();

        channel.notifyOrderStatus(order, OrderStatus.READY);

        verifyNoInteractions(apiClient, messageLogger, configRepository);
    }

    @Test
    @DisplayName("linked subscriber is inactive -> no send, no config lookup, no log")
    void notifyOrderStatus_inactiveSubscriber_skipsSendAndLog() {
        InstagramSubscriber inactive = activeSubscriber();
        inactive.setIsActive(false);
        when(subscriberRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of(inactive));
        Order order = Order.builder().orderNumber("ORD-1").customer(customer()).build();

        channel.notifyOrderStatus(order, OrderStatus.READY);

        verifyNoInteractions(apiClient, messageLogger, configRepository);
    }

    @Test
    @DisplayName("subscriber's restaurant has no active Instagram config -> no send, no log")
    void notifyOrderStatus_noActiveConfig_skipsSendAndLog() {
        InstagramSubscriber subscriber = activeSubscriber();
        when(subscriberRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of(subscriber));
        when(configRepository.findByRestaurantIdAndIsActiveTrue(RESTAURANT_ID)).thenReturn(Optional.empty());
        Order order = Order.builder().orderNumber("ORD-1").customer(customer()).build();

        channel.notifyOrderStatus(order, OrderStatus.READY);

        verifyNoInteractions(apiClient, messageLogger);
    }

    // ---------------------------------------------------------------- multiple subscribers per customer

    @Test
    @DisplayName("two linked subscribers in different restaurants -> each messaged through its OWN restaurant's config")
    void notifyOrderStatus_multipleSubscribers_eachThroughOwnRestaurantConfig() {
        InstagramSubscriber subA = InstagramSubscriber.builder().id(1L).restaurantId(10L).igsid("igA").isActive(true).build();
        InstagramSubscriber subB = InstagramSubscriber.builder().id(2L).restaurantId(20L).igsid("igB").isActive(true).build();
        InstagramBotConfig configA = InstagramBotConfig.builder().id(10L).restaurantId(10L).isActive(true).build();
        InstagramBotConfig configB = InstagramBotConfig.builder().id(20L).restaurantId(20L).isActive(true).build();

        when(subscriberRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of(subA, subB));
        when(configRepository.findByRestaurantIdAndIsActiveTrue(10L)).thenReturn(Optional.of(configA));
        when(configRepository.findByRestaurantIdAndIsActiveTrue(20L)).thenReturn(Optional.of(configB));
        when(apiClient.sendMessage(any(), anyString(), anyString())).thenReturn(InstagramSendResult.ok());

        Order order = Order.builder().orderNumber("ORD-1").customer(customer()).build();
        channel.notifyOrderStatus(order, OrderStatus.READY);

        verify(apiClient).sendMessage(eq(configA), eq("igA"), anyString());
        verify(apiClient).sendMessage(eq(configB), eq("igB"), anyString());
    }

    @Test
    @DisplayName("one linked subscriber's send blowing up does not stop the other linked subscriber from being messaged")
    void notifyOrderStatus_oneSubscriberThrows_otherStillMessaged() {
        InstagramSubscriber subA = InstagramSubscriber.builder().id(1L).restaurantId(10L).igsid("igA").isActive(true).build();
        InstagramSubscriber subB = InstagramSubscriber.builder().id(2L).restaurantId(20L).igsid("igB").isActive(true).build();
        InstagramBotConfig configB = InstagramBotConfig.builder().id(20L).restaurantId(20L).isActive(true).build();

        when(subscriberRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of(subA, subB));
        when(configRepository.findByRestaurantIdAndIsActiveTrue(10L)).thenThrow(new RuntimeException("boom"));
        when(configRepository.findByRestaurantIdAndIsActiveTrue(20L)).thenReturn(Optional.of(configB));
        when(apiClient.sendMessage(any(), anyString(), anyString())).thenReturn(InstagramSendResult.ok());

        Order order = Order.builder().orderNumber("ORD-1").customer(customer()).build();
        channel.notifyOrderStatus(order, OrderStatus.READY);

        verify(apiClient).sendMessage(eq(configB), eq("igB"), anyString());
    }
}

package com.elcafe.modules.customer.service;

import com.elcafe.common.event.CustomerDeletedEvent;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.dto.CustomerPreferenceRequest;
import com.elcafe.modules.customer.dto.CustomerProfileResponse;
import com.elcafe.modules.customer.dto.CustomerTimelineEntry;
import com.elcafe.modules.customer.dto.CustomerTimelineResponse;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.entity.CustomerPreference;
import com.elcafe.modules.customer.repository.CustomerPreferenceRepository;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.instagram.entity.InstagramInboundMessage;
import com.elcafe.modules.instagram.entity.InstagramLog;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.repository.InstagramInboundMessageRepository;
import com.elcafe.modules.instagram.repository.InstagramLogRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.sms.entity.SmsLog;
import com.elcafe.modules.sms.repository.SmsLogRepository;
import com.elcafe.modules.telegram.repository.TelegramLogRepository;
import com.elcafe.modules.telegram.repository.TelegramSubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The customer 360 aggregation. Two things carry real risk here: money arithmetic that must ignore
 * orders that never happened, and a conversation stream merged from tables that disagree about how they
 * store time — get that wrong and channels interleave in the wrong order.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerProfileServiceTest {

    private static final Long TENANT = 3L;
    private static final Long OTHER_TENANT = 99L;
    private static final Long CUSTOMER = 7L;

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerPreferenceRepository preferenceRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CustomerLoyaltyRepository customerLoyaltyRepository;
    @Mock private InstagramSubscriberRepository instagramSubscriberRepository;
    @Mock private InstagramInboundMessageRepository instagramInboundMessageRepository;
    @Mock private InstagramLogRepository instagramLogRepository;
    @Mock private TelegramSubscriberRepository telegramSubscriberRepository;
    @Mock private TelegramLogRepository telegramLogRepository;
    @Mock private SmsLogRepository smsLogRepository;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;

    @InjectMocks private CustomerProfileService service;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = Customer.builder().id(CUSTOMER).restaurantId(TENANT)
                .firstName("Dilnoza").phone("+998901112233").build();
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        when(customerRepository.findById(CUSTOMER)).thenReturn(Optional.of(customer));
        // Quiet defaults — individual tests override what they care about.
        when(orderRepository.findByCustomer_IdOrderByCreatedAtDesc(anyLong(), any(Pageable.class)))
                .thenReturn(List.of());
        when(customerLoyaltyRepository.findByCustomerId(anyLong())).thenReturn(Optional.empty());
        when(preferenceRepository.findByCustomer_IdOrderByPreferenceTypeAscValueAsc(anyLong()))
                .thenReturn(List.of());
        when(instagramSubscriberRepository.findByCustomerId(anyLong())).thenReturn(List.of());
        when(telegramSubscriberRepository.findAllByCustomerId(anyLong())).thenReturn(List.of());
        when(smsLogRepository.findByCustomerId(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
    }

    private Order order(OrderStatus status, String total, OffsetDateTime at, OrderItem... items) {
        return Order.builder().status(status).total(new BigDecimal(total)).createdAt(at)
                .items(List.of(items)).build();
    }

    private OrderItem item(String name, int qty) {
        return OrderItem.builder().productName(name).quantity(qty).build();
    }

    // ---- purchase statistics ------------------------------------------------------------------

    @Test
    @DisplayName("cancelled orders never count toward spend, order count or the average")
    void cancelledOrdersExcluded() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(orderRepository.findByCustomer_IdOrderByCreatedAtDesc(eq(CUSTOMER), any(Pageable.class)))
                .thenReturn(List.of(
                        order(OrderStatus.COMPLETED, "30000", now, item("Choy", 1)),
                        order(OrderStatus.CANCELLED, "99999", now.minusDays(1), item("Tort", 1)),
                        order(OrderStatus.DELIVERED, "10000", now.minusDays(2), item("Choy", 2))));

        CustomerProfileResponse.PurchaseStats stats = service.getProfile(CUSTOMER).getPurchases();

        assertThat(stats.getOrderCount()).isEqualTo(2);
        assertThat(stats.getLifetimeSpend()).isEqualByComparingTo("40000");   // the 99999 is excluded
        assertThat(stats.getAverageOrderValue()).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("first and last order come from the ends of the history, not the order rows arrive in")
    void firstAndLastOrderBoundTheHistory() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(orderRepository.findByCustomer_IdOrderByCreatedAtDesc(eq(CUSTOMER), any(Pageable.class)))
                .thenReturn(List.of(
                        order(OrderStatus.COMPLETED, "10000", now),
                        order(OrderStatus.COMPLETED, "10000", now.minusDays(30))));

        CustomerProfileResponse.PurchaseStats stats = service.getProfile(CUSTOMER).getPurchases();

        assertThat(stats.getLastOrderAt()).isEqualTo(now);
        assertThat(stats.getFirstOrderAt()).isEqualTo(now.minusDays(30));
    }

    @Test
    @DisplayName("top items rank by quantity ordered, not by how many orders mention them")
    void topItemsRankByQuantity() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(orderRepository.findByCustomer_IdOrderByCreatedAtDesc(eq(CUSTOMER), any(Pageable.class)))
                .thenReturn(List.of(
                        order(OrderStatus.COMPLETED, "10000", now, item("Choy", 1), item("Tort", 5)),
                        order(OrderStatus.COMPLETED, "10000", now.minusDays(1), item("Choy", 2))));

        List<CustomerProfileResponse.TopItem> top = service.getProfile(CUSTOMER).getPurchases().getTopItems();

        assertThat(top).extracting(CustomerProfileResponse.TopItem::getProductName)
                .containsExactly("Tort", "Choy");   // 5 beats 3, despite Choy appearing in more orders
    }

    @Test
    @DisplayName("a guest with no orders reports zeros rather than nulls")
    void noOrdersIsZeroNotNull() {
        CustomerProfileResponse.PurchaseStats stats = service.getProfile(CUSTOMER).getPurchases();

        assertThat(stats.getOrderCount()).isZero();
        assertThat(stats.getLifetimeSpend()).isEqualByComparingTo("0");
        assertThat(stats.getTopItems()).isEmpty();
    }

    // ---- tenant scoping -----------------------------------------------------------------------

    @Test
    @DisplayName("another restaurant's customer reads as not-found, never as forbidden")
    void foreignCustomerIsNotFound() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(OTHER_TENANT);

        assertThatThrownBy(() -> service.getProfile(CUSTOMER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- preferences --------------------------------------------------------------------------

    @Test
    @DisplayName("the same preference cannot be recorded twice")
    void duplicatePreferenceRejected() {
        when(preferenceRepository.existsByCustomer_IdAndPreferenceTypeAndValueIgnoreCase(
                CUSTOMER, CustomerPreference.Type.ALLERGY, "walnuts")).thenReturn(true);

        assertThatThrownBy(() -> service.addPreference(CUSTOMER,
                CustomerPreferenceRequest.builder()
                        .preferenceType(CustomerPreference.Type.ALLERGY).value("walnuts").build(),
                42L))
                .isInstanceOf(BadRequestException.class);
        verify(preferenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("a preference recorded through the API is always MANUAL and stamped with who added it")
    void preferenceIsManualAndAttributed() {
        when(preferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.addPreference(CUSTOMER, CustomerPreferenceRequest.builder()
                .preferenceType(CustomerPreference.Type.DISLIKE).value("  coriander  ").build(), 42L);

        var captor = org.mockito.ArgumentCaptor.forClass(CustomerPreference.class);
        verify(preferenceRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(CustomerPreference.Source.MANUAL);
        assertThat(captor.getValue().getCreatedByUserId()).isEqualTo(42L);
        assertThat(captor.getValue().getValue()).isEqualTo("coriander");     // trimmed
        assertThat(captor.getValue().getRestaurantId()).isEqualTo(TENANT);
    }

    // ---- timeline -----------------------------------------------------------------------------

    private void instagramHistory(OffsetDateTime inboundAt, OffsetDateTime outboundAt) {
        when(instagramSubscriberRepository.findByCustomerId(CUSTOMER))
                .thenReturn(List.of(InstagramSubscriber.builder().id(1L).build()));
        when(instagramInboundMessageRepository.findByRestaurantIdAndSubscriberIdInOrderByReceivedAtDesc(
                eq(TENANT), any(), any(Pageable.class)))
                .thenReturn(List.of(InstagramInboundMessage.builder()
                        .messageText("is the cake gluten free?").receivedAt(inboundAt).build()));
        when(instagramLogRepository.findBySubscriberIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(InstagramLog.builder()
                        .message("Yes, it is!").createdAt(outboundAt).build()));
    }

    @Test
    @DisplayName("channels merge into one stream ordered newest first, regardless of which table they came from")
    void mergesChannelsNewestFirst() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        instagramHistory(now.minusHours(2), now.minusHours(1));
        // SMS stores LocalDateTime — it must still sort correctly against Instagram's OffsetDateTime.
        when(smsLogRepository.findByCustomerId(eq(CUSTOMER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(SmsLog.builder()
                        .message("Your order is ready")
                        .createdAt(LocalDateTime.now().minusMinutes(10))
                        .build())));

        CustomerTimelineResponse timeline = service.getTimeline(CUSTOMER, null, 20);

        assertThat(timeline.getEntries()).hasSize(3);
        assertThat(timeline.getEntries().get(0).getChannel())
                .isEqualTo(CustomerTimelineEntry.Channel.SMS);            // most recent
        assertThat(timeline.getEntries().get(2).getDirection())
                .isEqualTo(CustomerTimelineEntry.Direction.IN);           // oldest, the guest's question
        assertThat(timeline.getEntries())
                .isSortedAccordingTo(java.util.Comparator.comparing(
                        CustomerTimelineEntry::getTimestamp).reversed());
    }

    @Test
    @DisplayName("the before cursor returns only older messages, so load-more cannot repeat a line")
    void cursorReturnsOnlyOlderMessages() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        instagramHistory(now.minusHours(5), now.minusHours(1));

        CustomerTimelineResponse timeline = service.getTimeline(CUSTOMER, now.minusHours(2), 20);

        assertThat(timeline.getEntries()).hasSize(1);
        assertThat(timeline.getEntries().get(0).getDirection())
                .isEqualTo(CustomerTimelineEntry.Direction.IN);
        assertThat(timeline.getEntries().get(0).getTimestamp()).isBefore(now.minusHours(2));
    }

    @Test
    @DisplayName("a full window reports hasMore and a cursor that resumes where it stopped")
    void reportsHasMoreAndCursor() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        instagramHistory(now.minusHours(2), now.minusHours(1));

        CustomerTimelineResponse timeline = service.getTimeline(CUSTOMER, null, 1);

        assertThat(timeline.getEntries()).hasSize(1);
        assertThat(timeline.getHasMore()).isTrue();
        assertThat(timeline.getNextCursor()).isEqualTo(timeline.getEntries().get(0).getTimestamp());
    }

    @Test
    @DisplayName("a guest with no messages anywhere gets an empty timeline, not an error")
    void emptyTimeline() {
        CustomerTimelineResponse timeline = service.getTimeline(CUSTOMER, null, 20);

        assertThat(timeline.getEntries()).isEmpty();
        assertThat(timeline.getHasMore()).isFalse();
        assertThat(timeline.getNextCursor()).isNull();
    }

    /**
     * Right to erasure. Preferences hold allergies and dietary needs — the most sensitive thing on the
     * profile — and they used to leave only because V183's foreign key cascaded. The JPA mapping never
     * said so, which made the guarantee a property of the Flyway schema rather than of the code.
     *
     * <p>Deliberately asserts no tenant check runs first: the caller here is the deletion transaction,
     * not a signed-in operator, and requiring a tenant scope would make erasure fail for exactly the
     * background paths that need it most.
     */
    @Test
    @DisplayName("the erasure event removes the guest's preferences, without needing a signed-in tenant")
    void erasureRemovesPreferences() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(null);

        service.onCustomerDeleted(new CustomerDeletedEvent(CUSTOMER));

        verify(preferenceRepository).deleteByCustomer_Id(CUSTOMER);
    }
}

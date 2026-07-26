package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.entity.InstagramSubscriberAddress;
import com.elcafe.modules.instagram.enums.InstagramInboundKind;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberAddressRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.BusinessHoursRepository;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
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
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave 7 — the in-DM ordering flow. Same pure-Mockito harness as {@link InstagramBotServiceWizardTest}:
 * a mock {@link PlatformTransactionManager} makes the service's {@code TransactionTemplate} run its
 * callback inline, and {@code subscriberRepository.findByIgsidAndRestaurantId} returns the SAME mutable
 * subscriber instance every turn (as a committed re-read would carry state forward), so a multi-turn
 * conversation can be driven one {@code handleIncomingMessage} at a time and the cart/state asserted
 * between turns. {@code orderService} is mocked and captured — this exercises the state machine and the
 * cart→Order assembly, not a real DB (the JSONB cart's persistence is proven separately in
 * {@code InstagramSubscriberCartPersistenceTest}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramBotServiceOrderingTest {

    private static final Long RESTAURANT = 7L;
    private static final String IGSID = "igsid-order";

    @Mock private InstagramBotConfigRepository configRepository;
    @Mock private InstagramSubscriberRepository subscriberRepository;
    @Mock private InstagramSubscriberAddressRepository addressRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private BusinessHoursRepository businessHoursRepository;
    @Mock private InstagramApiClient apiClient;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @Mock private InstagramMessageLogger messageLogger;
    @Mock private ProductRepository productRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private OrderService orderService;
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private InstagramBotService service;

    private InstagramBotConfig config;

    @BeforeEach
    void setUp() {
        config = InstagramBotConfig.builder().restaurantId(RESTAURANT).isActive(true).build();
        when(subscriberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(addressRepository.findAllBySubscriber(any())).thenReturn(List.of());
        // createOrder echoes its argument back with an order number stamped, like the real service.
        lenient().when(orderService.createOrder(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setOrderNumber("ORD-20260726-0001");
            return o;
        });
        lenient().when(restaurantRepository.findById(RESTAURANT))
                .thenReturn(Optional.of(new Restaurant()));
    }

    // ---- fixtures ----

    private InstagramSubscriber subscriberInState(String state) {
        InstagramSubscriber s = InstagramSubscriber.builder()
                .id(1L).restaurantId(RESTAURANT).igsid(IGSID)
                .displayName("Dilnoza").phone("+998901112233")
                .conversationState(state).isActive(true).isBlocked(false)
                .build();
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT))
                .thenReturn(Optional.of(s));
        return s;
    }

    private InstagramSubscriber registered() {
        return subscriberInState("REGISTERED");
    }

    private Product product(Long id, String name, String price) {
        return Product.builder().id(id).name(name).price(new BigDecimal(price))
                .status(ProductStatus.LIVE).build();
    }

    private void menu(Product... products) {
        when(productRepository.findByRestaurant_IdAndStatus(RESTAURANT, ProductStatus.LIVE))
                .thenReturn(List.of(products));
    }

    private void savedAddress(String address, boolean isDefault) {
        when(addressRepository.findAllBySubscriber(any())).thenReturn(List.of(
                InstagramSubscriberAddress.builder()
                        .restaurantId(RESTAURANT).address(address).isDefault(isDefault).build()));
    }

    private void text(String t) {
        service.handleIncomingMessage(config, IGSID, null, InstagramInboundKind.TEXT, t, null);
    }

    private void tap(String payload) {
        service.handleIncomingMessage(config, IGSID, null, InstagramInboundKind.QUICK_REPLY, null, payload);
    }

    // -------------------------------------------------------------------------
    // Entry gating — only a REGISTERED subscriber can order (opt-out precedence is proved separately).
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("a non-REGISTERED subscriber cannot enter the ordering flow via an order keyword")
    void nonRegisteredCannotOrderViaKeyword() {
        InstagramSubscriber s = subscriberInState("AWAITING_PHONE");   // still mid-registration

        text("buyurtma");   // an order-intent keyword

        // "buyurtma" is consumed as (invalid) phone input, never as an order: the menu is never listed
        // and no order is created; the subscriber stays exactly where they were.
        assertThat(s.getConversationState()).isEqualTo("AWAITING_PHONE");
        verify(productRepository, never()).findByRestaurant_IdAndStatus(any(), any());
        verify(orderService, never()).createOrder(any());
    }

    @Test
    @DisplayName("a non-REGISTERED subscriber cannot enter the ordering flow via a stale ORDER button")
    void nonRegisteredCannotOrderViaOrderPayload() {
        InstagramSubscriber s = subscriberInState("AWAITING_ADDRESS");

        tap("ORDER");

        assertThat(s.getConversationState()).isEqualTo("AWAITING_ADDRESS");
        verify(productRepository, never()).findByRestaurant_IdAndStatus(any(), any());
        verify(orderService, never()).createOrder(any());
    }

    // -------------------------------------------------------------------------
    // The happy path: cart accumulation across turns, and a real INSTAGRAM_BOT order at checkout.
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("a full multi-turn order accumulates a cart and creates an INSTAGRAM_BOT order with the "
            + "right quantities and total")
    void fullOrderFlowAccumulatesCartAndCreatesOrder() {
        InstagramSubscriber s = registered();
        menu(product(10L, "Choy", "8000"), product(20L, "Tort", "25000"));

        text("buyurtma");                       // enter ordering
        assertThat(s.getConversationState()).isEqualTo("ORDER_BROWSING");

        text("1");                              // pick Choy (menu item #1)
        assertThat(s.getConversationState()).isEqualTo("ORDER_QUANTITY");
        assertThat(s.getOrderCart()).hasSize(1);

        text("2");                              // 2x Choy
        assertThat(s.getConversationState()).isEqualTo("ORDER_CONFIRMING");
        assertThat(s.getOrderCart().get(0).getQuantity()).isEqualTo(2);

        tap("ORDER_ADD_MORE");                  // add another product
        assertThat(s.getConversationState()).isEqualTo("ORDER_BROWSING");

        tap("ORDER_ITEM_20");                   // pick Tort by button
        assertThat(s.getConversationState()).isEqualTo("ORDER_QUANTITY");
        assertThat(s.getOrderCart()).hasSize(2);   // <-- cart accumulated across turns

        text("3");                              // 3x Tort
        assertThat(s.getConversationState()).isEqualTo("ORDER_CONFIRMING");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        tap("ORDER_CHECKOUT");

        verify(orderService).createOrder(captor.capture());
        Order created = captor.getValue();

        assertThat(created.getOrderSource()).isEqualTo(OrderSource.INSTAGRAM_BOT);
        assertThat(created.getItems()).hasSize(2);

        Map<Long, Integer> qtyByProduct = created.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));
        assertThat(qtyByProduct).containsEntry(10L, 2).containsEntry(20L, 3);

        Map<Long, BigDecimal> lineTotalByProduct = created.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getTotalPrice));
        assertThat(lineTotalByProduct.get(10L)).isEqualByComparingTo("16000"); // 8000 x 2
        assertThat(lineTotalByProduct.get(20L)).isEqualByComparingTo("75000"); // 25000 x 3

        // 16000 + 75000
        assertThat(created.getSubtotal()).isEqualByComparingTo("91000");
        assertThat(created.getTotal()).isEqualByComparingTo("91000");

        // After checkout: cart cleared, back to REGISTERED.
        assertThat(s.getOrderCart()).isNull();
        assertThat(s.getConversationState()).isEqualTo("REGISTERED");
    }

    @Test
    @DisplayName("checkout with a saved default address produces a DELIVERY order to that address")
    void checkoutWithSavedAddressIsDelivery() {
        InstagramSubscriber s = registered();
        menu(product(10L, "Choy", "8000"));
        savedAddress("Chilonzor 5, uy 12", true);

        text("buyurtma");
        tap("ORDER_ITEM_10");
        tap("ORDER_QTY_2");                     // quantity via quick reply
        assertThat(s.getConversationState()).isEqualTo("ORDER_CONFIRMING");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        tap("ORDER_CHECKOUT");

        verify(orderService).createOrder(captor.capture());
        Order created = captor.getValue();
        assertThat(created.getOrderType()).isEqualTo(OrderType.DELIVERY);
        assertThat(created.getOrderSource()).isEqualTo(OrderSource.INSTAGRAM_BOT);
        assertThat(created.getItems()).hasSize(1);
        assertThat(created.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(created.getTotal()).isEqualByComparingTo("16000");
        assertThat(created.getCustomerNotes()).contains("Chilonzor 5, uy 12");
    }

    @Test
    @DisplayName("with no saved address the order is TAKEAWAY")
    void checkoutWithoutAddressIsTakeaway() {
        InstagramSubscriber s = registered();
        menu(product(10L, "Choy", "8000"));

        text("buyurtma");
        tap("ORDER_ITEM_10");
        tap("ORDER_QTY_1");
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        tap("ORDER_CHECKOUT");

        verify(orderService).createOrder(captor.capture());
        assertThat(captor.getValue().getOrderType()).isEqualTo(OrderType.TAKEAWAY);
    }

    // -------------------------------------------------------------------------
    // Cancel — from any ordering step, cart cleared, back to REGISTERED, no order.
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("the Cancel button aborts an in-progress order, clears the cart, returns to REGISTERED")
    void cancelButtonAbortsOrder() {
        InstagramSubscriber s = registered();
        menu(product(10L, "Choy", "8000"));

        text("buyurtma");
        text("1");
        text("2");
        assertThat(s.getOrderCart()).hasSize(1);

        tap("ORDER_CANCEL");

        assertThat(s.getConversationState()).isEqualTo("REGISTERED");
        assertThat(s.getOrderCart()).isNull();
        verify(orderService, never()).createOrder(any());
    }

    @Test
    @DisplayName("typing /cancel mid-quantity aborts the order")
    void slashCancelAbortsOrder() {
        InstagramSubscriber s = registered();
        menu(product(10L, "Choy", "8000"));

        text("buyurtma");
        text("1");                              // now in ORDER_QUANTITY
        assertThat(s.getConversationState()).isEqualTo("ORDER_QUANTITY");

        text("/cancel");

        assertThat(s.getConversationState()).isEqualTo("REGISTERED");
        assertThat(s.getOrderCart()).isNull();
        verify(orderService, never()).createOrder(any());
    }

    // -------------------------------------------------------------------------
    // Invariant: opt-out ALWAYS wins, even mid-order (process() checks it before any wizard dispatch).
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("STOP mid-order opts the subscriber out and never creates an order")
    void optOutWinsMidOrder() {
        InstagramSubscriber s = registered();
        menu(product(10L, "Choy", "8000"));

        text("buyurtma");
        text("1");                              // mid-order (ORDER_QUANTITY)

        text("stop");                           // opt-out keyword — must be intercepted before dispatch

        assertThat(s.getMarketingOptIn()).isFalse();
        verify(orderService, never()).createOrder(any());
    }

    // -------------------------------------------------------------------------
    // Menu edge case.
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("a restaurant with no live products cannot start an order and stays REGISTERED")
    void emptyMenuCannotStartOrder() {
        InstagramSubscriber s = registered();
        menu();   // no live products

        text("buyurtma");

        assertThat(s.getConversationState()).isEqualTo("REGISTERED");
        assertThat(s.getOrderCart()).isNull();
        verify(orderService, never()).createOrder(any());
    }
}

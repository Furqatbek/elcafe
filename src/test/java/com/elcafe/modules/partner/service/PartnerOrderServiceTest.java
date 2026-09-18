package com.elcafe.modules.partner.service;

import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.AddOnRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.modules.partner.dto.PartnerOrderRequest;
import com.elcafe.modules.partner.dto.PartnerOrderResponse;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerOrder;
import com.elcafe.modules.partner.exception.PartnerOrderRejectedException;
import com.elcafe.modules.partner.repository.PartnerOrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the partner order contract, and one invariant above all: the push must go through
 * {@link OrderService#createOrder}. Printing the kitchen ticket hangs off that method, so an
 * implementation that "optimises" it into an {@code orderRepository.save} would still pass every
 * assertion about totals and statuses while quietly never printing an aggregator order again — which is
 * precisely the bug this whole integration exists to avoid.
 */
@ExtendWith(MockitoExtension.class)
class PartnerOrderServiceTest {

    @Mock private RestaurantRepository restaurantRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private AddOnRepository addOnRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PartnerOrderRepository partnerOrderRepository;
    @Mock private OrderService orderService;

    @InjectMocks private PartnerOrderService service;

    private static final Long RESTAURANT_ID = 3L;

    private Partner partner;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "autoAccept", false);
        partner = Partner.builder().id(7L).name("Test Aggregator").slug("test-agg").active(true).build();

        restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("Test Restaurant");
        restaurant.setActive(true);
        restaurant.setAcceptingOrders(true);
        restaurant.setDeliveryFee(new BigDecimal("5000"));
    }

    /** A product that genuinely belongs to this restaurant, via the category the guard walks. */
    private Product productOf(Long id, String name, String price, Restaurant owner) {
        Category category = new Category();
        category.setId(100L);
        category.setRestaurant(owner);

        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setInStock(true);
        product.setCategory(category);
        return product;
    }

    private PartnerOrderRequest.PartnerOrderRequestBuilder baseRequest() {
        return PartnerOrderRequest.builder()
                .restaurantId(RESTAURANT_ID)
                .externalOrderId("EXT-1")
                .orderType(OrderType.TAKEAWAY)
                .paymentMode(PartnerOrderRequest.PaymentMode.PREPAID)
                .items(List.of(PartnerOrderRequest.Item.builder()
                        .productId(1L).quantity(2).build()));
    }

    private void expectNoExistingMapping() {
        when(partnerOrderRepository.findByPartnerIdAndExternalOrderId(7L, "EXT-1"))
                .thenReturn(Optional.empty());
    }

    private void expectRestaurant() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
    }

    /** createOrder normally assigns these; the mock stands in for it. */
    private void expectCreateOrderEchoes() {
        when(orderService.createOrder(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(500L);
            order.setOrderNumber("ORD-500");
            order.setStatus(OrderStatus.NEW);
            return order;
        });
    }

    @Test
    @DisplayName("a valid push is created through OrderService.createOrder — the path that prints")
    void pushOrder_goesThroughCreateOrder() {
        expectNoExistingMapping();
        expectRestaurant();
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(productOf(1L, "Plov", "30000", restaurant)));
        expectCreateOrderEchoes();

        PartnerOrderResponse response = service.pushOrder(partner, baseRequest().build());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderService).createOrder(captor.capture());
        // Never the repository directly — that is the path with no printer on it.
        verify(orderRepository, never()).save(any(Order.class));

        Order built = captor.getValue();
        assertThat(built.getOrderSource()).isEqualTo(OrderSource.AGGREGATOR);
        assertThat(built.getSubtotal()).isEqualByComparingTo("60000");
        // TAKEAWAY carries no delivery fee.
        assertThat(built.getDeliveryFee()).isEqualByComparingTo("0");
        assertThat(built.getTotal()).isEqualByComparingTo("60000");
        assertThat(response.getDuplicate()).isFalse();
        assertThat(response.getOrderNumber()).isEqualTo("ORD-500");
    }

    @Test
    @DisplayName("PREPAID arrives settled so the venue is not shown a debt that will never be collected")
    void pushOrder_prepaid_isSettled() {
        expectNoExistingMapping();
        expectRestaurant();
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(productOf(1L, "Plov", "30000", restaurant)));
        expectCreateOrderEchoes();

        service.pushOrder(partner, baseRequest().build());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderService).createOrder(captor.capture());
        assertThat(captor.getValue().getPayment().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(captor.getValue().getPayment().getPaymentGateway()).isEqualTo("test-agg");
    }

    @Test
    @DisplayName("CASH arrives pending — the money is collected on handover")
    void pushOrder_cash_isPending() {
        expectNoExistingMapping();
        expectRestaurant();
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(productOf(1L, "Plov", "30000", restaurant)));
        expectCreateOrderEchoes();

        service.pushOrder(partner,
                baseRequest().paymentMode(PartnerOrderRequest.PaymentMode.CASH).build());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderService).createOrder(captor.capture());
        assertThat(captor.getValue().getPayment().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("DELIVERY adds the venue's delivery fee")
    void pushOrder_delivery_chargesFee() {
        expectNoExistingMapping();
        expectRestaurant();
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(productOf(1L, "Plov", "30000", restaurant)));
        expectCreateOrderEchoes();

        service.pushOrder(partner, baseRequest()
                .orderType(OrderType.DELIVERY)
                .delivery(PartnerOrderRequest.Delivery.builder().address("12 Test St").build())
                .build());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderService).createOrder(captor.capture());
        assertThat(captor.getValue().getDeliveryFee()).isEqualByComparingTo("5000");
        assertThat(captor.getValue().getTotal()).isEqualByComparingTo("65000");
    }

    @Test
    @DisplayName("an unknown product id is rejected with the offending id, and nothing is created")
    void pushOrder_unknownProduct_rejected() {
        expectNoExistingMapping();
        expectRestaurant();
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pushOrder(partner, baseRequest().build()))
                .isInstanceOf(PartnerOrderRejectedException.class)
                .satisfies(thrown -> {
                    PartnerOrderRejectedException ex = (PartnerOrderRejectedException) thrown;
                    assertThat(ex.getReason())
                            .isEqualTo(PartnerOrderRejectedException.Reason.UNKNOWN_ITEMS);
                    assertThat(ex.getDetails()).containsEntry("unknownProductIds", List.of(1L));
                });

        verify(orderService, never()).createOrder(any());
    }

    @Test
    @DisplayName("a product belonging to ANOTHER restaurant reads as unknown, not as purchasable")
    void pushOrder_crossVenueProduct_rejected() {
        expectNoExistingMapping();
        expectRestaurant();
        Restaurant other = new Restaurant();
        other.setId(99L);
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(productOf(1L, "Someone else's plov", "30000", other)));

        assertThatThrownBy(() -> service.pushOrder(partner, baseRequest().build()))
                .isInstanceOf(PartnerOrderRejectedException.class)
                .satisfies(thrown -> assertThat(((PartnerOrderRejectedException) thrown).getReason())
                        .isEqualTo(PartnerOrderRejectedException.Reason.UNKNOWN_ITEMS));

        verify(orderService, never()).createOrder(any());
    }

    @Test
    @DisplayName("an out-of-stock product is a 409-shaped rejection, distinct from an unknown one")
    void pushOrder_outOfStock_rejected() {
        expectNoExistingMapping();
        expectRestaurant();
        Product soldOut = productOf(1L, "Plov", "30000", restaurant);
        soldOut.setInStock(false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(soldOut));

        assertThatThrownBy(() -> service.pushOrder(partner, baseRequest().build()))
                .isInstanceOf(PartnerOrderRejectedException.class)
                .satisfies(thrown -> {
                    PartnerOrderRejectedException ex = (PartnerOrderRejectedException) thrown;
                    assertThat(ex.getReason())
                            .isEqualTo(PartnerOrderRejectedException.Reason.ITEMS_UNAVAILABLE);
                    assertThat(ex.getDetails()).containsEntry("unavailableProductIds", List.of(1L));
                });

        verify(orderService, never()).createOrder(any());
    }

    @Test
    @DisplayName("a disagreement on price refuses the order rather than silently repricing it")
    void pushOrder_priceMismatch_rejected() {
        expectNoExistingMapping();
        expectRestaurant();
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(productOf(1L, "Plov", "30000", restaurant)));

        // The partner is selling from a stale menu at 25000 each; ours says 30000.
        assertThatThrownBy(() -> service.pushOrder(partner,
                baseRequest().expectedTotal(new BigDecimal("50000")).build()))
                .isInstanceOf(PartnerOrderRejectedException.class)
                .satisfies(thrown -> {
                    PartnerOrderRejectedException ex = (PartnerOrderRejectedException) thrown;
                    assertThat(ex.getReason())
                            .isEqualTo(PartnerOrderRejectedException.Reason.PRICE_MISMATCH);
                    // Both numbers are returned so their system can log the gap, not just "no".
                    assertThat(ex.getDetails()).containsEntry("expectedTotal", new BigDecimal("50000"));
                    assertThat(ex.getDetails()).containsKey("actualTotal");
                });

        verify(orderService, never()).createOrder(any());
    }

    @Test
    @DisplayName("a matching expectedTotal passes straight through")
    void pushOrder_matchingExpectedTotal_accepted() {
        expectNoExistingMapping();
        expectRestaurant();
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(productOf(1L, "Plov", "30000", restaurant)));
        expectCreateOrderEchoes();

        PartnerOrderResponse response = service.pushOrder(partner,
                baseRequest().expectedTotal(new BigDecimal("60000")).build());

        assertThat(response.getTotal()).isEqualByComparingTo("60000");
    }

    @Test
    @DisplayName("a re-push returns the original order flagged duplicate, and creates nothing")
    void pushOrder_replay_returnsOriginal() {
        Order original = Order.builder().id(500L).orderNumber("ORD-500")
                .status(OrderStatus.ACCEPTED).subtotal(new BigDecimal("60000"))
                .deliveryFee(BigDecimal.ZERO).total(new BigDecimal("60000"))
                .items(List.of()).build();
        when(partnerOrderRepository.findByPartnerIdAndExternalOrderId(7L, "EXT-1"))
                .thenReturn(Optional.of(PartnerOrder.builder().partnerId(7L).restaurantId(RESTAURANT_ID)
                        .externalOrderId("EXT-1").orderId(500L).build()));
        when(orderRepository.findById(500L)).thenReturn(Optional.of(original));

        PartnerOrderResponse response = service.pushOrder(partner, baseRequest().build());

        assertThat(response.getDuplicate()).isTrue();
        assertThat(response.getOrderNumber()).isEqualTo("ORD-500");
        // The whole point: a retried push must not cook the same lunch twice.
        verify(orderService, never()).createOrder(any());
    }

    @Test
    @DisplayName("a venue that has stopped taking orders refuses the push")
    void pushOrder_venueClosed_rejected() {
        expectNoExistingMapping();
        restaurant.setAcceptingOrders(false);
        expectRestaurant();

        assertThatThrownBy(() -> service.pushOrder(partner, baseRequest().build()))
                .isInstanceOf(PartnerOrderRejectedException.class)
                .satisfies(thrown -> assertThat(((PartnerOrderRejectedException) thrown).getReason())
                        .isEqualTo(PartnerOrderRejectedException.Reason.VENUE_NOT_ACCEPTING));

        verify(orderService, never()).createOrder(any());
    }

    @Test
    @DisplayName("auto-accept moves the order to ACCEPTED when enabled")
    void pushOrder_autoAccept_advancesStatus() {
        ReflectionTestUtils.setField(service, "autoAccept", true);
        expectNoExistingMapping();
        expectRestaurant();
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(productOf(1L, "Plov", "30000", restaurant)));
        expectCreateOrderEchoes();
        when(orderService.updateOrderStatus(any(), any(), any(), any()))
                .thenAnswer(invocation -> Order.builder().id(500L).orderNumber("ORD-500")
                        .status(OrderStatus.ACCEPTED).subtotal(new BigDecimal("60000"))
                        .deliveryFee(BigDecimal.ZERO).total(new BigDecimal("60000"))
                        .items(List.of()).build());

        PartnerOrderResponse response = service.pushOrder(partner, baseRequest().build());

        assertThat(response.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    @DisplayName("a failing auto-accept leaves the order standing — the ticket already printed")
    void pushOrder_autoAcceptFailure_doesNotFailThePush() {
        ReflectionTestUtils.setField(service, "autoAccept", true);
        expectNoExistingMapping();
        expectRestaurant();
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(productOf(1L, "Plov", "30000", restaurant)));
        expectCreateOrderEchoes();
        // Accepting deducts ingredients and can legitimately refuse.
        when(orderService.updateOrderStatus(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Insufficient ingredients"));

        PartnerOrderResponse response = service.pushOrder(partner, baseRequest().build());

        // The partner has already charged their customer; the order must survive at NEW.
        assertThat(response.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(response.getOrderNumber()).isEqualTo("ORD-500");
    }
}

package com.elcafe.security;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.kitchen.entity.KitchenOrder;
import com.elcafe.modules.kitchen.repository.KitchenOrderRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.entity.OrderItemAddOn;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.enums.WaiterRole;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the entity-endpoint payloads that clients actually consume (frontend consumption audit,
 * 2026-07-10) with {@code spring.jpa.open-in-view=false} — i.e. serialization runs with NO session,
 * exactly as production will after the OSIV flip. Every association asserted here would silently
 * serialize as {@code null} (Jackson Hibernate6 module) — or, for the computed payment properties,
 * throw inside the getter — if a loading path stopped initializing it. This test is the evidence that
 * the in-transaction hydration ({@code OrderJsonHydration}, kitchen hydration) keeps payloads intact
 * without open-in-view.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:osivpin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
        // THE point of this test: serialization must survive without the request-scoped session.
        "spring.jpa.open-in-view=false",
})
class OsivOffPayloadPinTest {

    private static final String AUTH = "Authorization";

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private RestaurantTableRepository tableRepository;
    @Autowired private WaiterRepository waiterRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private KitchenOrderRepository kitchenOrderRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long restaurantId;
    private Long orderId;
    private String adminToken;

    @BeforeAll
    void seed() {
        Restaurant restaurant = restaurantRepository.save(
                Restaurant.builder().name("OSIV Pin").address("1 Test St").active(true).build());
        restaurantId = restaurant.getId();

        User admin = userRepository.save(User.builder()
                .email("osiv.admin@test.com").password(passwordEncoder.encode("pw"))
                .firstName("O").lastName("A").role(UserRole.ADMIN).active(true).restaurantId(restaurantId)
                .build());
        adminToken = "Bearer " + jwtUtil.generateAccessToken(UserPrincipal.create(admin));

        Waiter waiter = new Waiter();
        waiter.setRestaurantId(restaurantId);
        waiter.setName("Pin Waiter");
        waiter.setPinCode("9911");
        waiter.setRole(WaiterRole.WAITER);
        waiter.setActive(true);
        waiter = waiterRepository.save(waiter);

        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setTableNumber("T1");
        table.setTableName("Pin Table");
        table.setStatus(RestaurantTable.TableStatus.AVAILABLE);
        table.setCapacity(4);
        table.setActive(true);
        table = tableRepository.save(table);

        Order order = Order.builder()
                .orderNumber("OSIV-1")
                .restaurant(restaurant)
                .waiter(waiter)
                .diningTable(table)
                .status(OrderStatus.ACCEPTED)
                .paymentStatus(PaymentStatus.PENDING)
                .orderType(OrderType.DINE_IN)
                .orderSource(OrderSource.WAITER)
                .shiftId(77L)
                .subtotal(new BigDecimal("100"))
                .deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(new BigDecimal("100"))
                .items(new ArrayList<>())
                .build();

        OrderItem pizza = OrderItem.builder()
                .productId(1L).productName("Pin Pizza").quantity(1)
                .unitPrice(new BigDecimal("60")).totalPrice(new BigDecimal("60"))
                .build();
        OrderItemAddOn cheese = OrderItemAddOn.builder()
                .addOnId(5L).addOnName("Extra cheese").addOnPrice(new BigDecimal("5")).quantity(1)
                .build();
        cheese.setOrderItem(pizza);
        pizza.getItemAddOns().add(cheese);
        order.addItem(pizza);

        OrderItem cola = OrderItem.builder()
                .productId(2L).productName("Pin Cola").quantity(2)
                .unitPrice(new BigDecimal("20")).totalPrice(new BigDecimal("40"))
                .build();
        order.addItem(cola);

        Payment payment = Payment.builder()
                .method(PaymentMethod.CASH).status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("100")).tipAmount(BigDecimal.ZERO).refundedAmount(BigDecimal.ZERO)
                .build();
        order.addPayment(payment);

        orderId = orderRepository.save(order).getId();

        kitchenOrderRepository.save(KitchenOrder.builder().order(order).build());
    }

    @Test
    @DisplayName("order detail: items, add-ons, waiter, table, payments, and the computed payment fields all serialize")
    void orderDetailPayloadSurvives() throws Exception {
        mvc.perform(get("/api/v1/orders/" + orderId).header(AUTH, adminToken))
                .andDo(r -> System.out.println("DETAIL_BODY=" + r.getResponse().getContentAsString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].productName", hasItems("Pin Pizza", "Pin Cola")))
                .andExpect(jsonPath("$.data.waiter.name").value("Pin Waiter"))
                .andExpect(jsonPath("$.data.diningTable.tableNumber").value("T1"))
                // The computed payment properties iterate the payments collection INSIDE their getters —
                // the Jackson Hibernate module cannot intercept that, so their presence proves payments
                // were initialized in-transaction. (The raw `payments` list is @JsonIgnore'd and has
                // never been part of the payload; clients read the singular computed `payment`.)
                .andExpect(jsonPath("$.data.fullyPaid").value(true))
                .andExpect(jsonPath("$.data.totalPaid").value(100.0))
                .andExpect(jsonPath("$.data.payment.method").value("CASH"));
    }

    @Test
    @DisplayName("order list page keeps items populated")
    void orderListPayloadSurvives() throws Exception {
        mvc.perform(get("/api/v1/orders").param("page", "0").param("size", "5").header(AUTH, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].items", notNullValue()))
                .andExpect(jsonPath("$.data.content[0].fullyPaid").value(true))
                .andExpect(jsonPath("$.data.content[0].payment.method").value("CASH"));
    }

    @Test
    @DisplayName("by-shift report keeps nested item add-ons (association within association)")
    void byShiftPayloadSurvives() throws Exception {
        mvc.perform(get("/api/v1/orders/by-shift/77").header(AUTH, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].waiter.name").value("Pin Waiter"))
                .andExpect(jsonPath("$.data[0].items[*].itemAddOns[*].addOnName", hasItems("Extra cheese")));
    }

    @Test
    @DisplayName("kitchen board keeps kitchenOrder.order.orderNumber (its one nested dependency)")
    void kitchenPayloadSurvives() throws Exception {
        mvc.perform(get("/api/v1/kitchen/orders/active")
                        .param("restaurantId", String.valueOf(restaurantId))
                        .header(AUTH, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].order.orderNumber").value("OSIV-1"));
    }
}

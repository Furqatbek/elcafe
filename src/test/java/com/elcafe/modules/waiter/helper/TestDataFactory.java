package com.elcafe.modules.waiter.helper;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.entity.RestaurantTable.TableStatus;
import com.elcafe.modules.waiter.dto.AddOrderItemRequest;
import com.elcafe.modules.waiter.dto.CreateOrderRequest;
import com.elcafe.modules.waiter.dto.CreateWaiterRequest;
import com.elcafe.modules.waiter.dto.UpdateOrderItemRequest;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.enums.WaiterRole;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating test data instances with sensible defaults.
 */
public final class TestDataFactory {

    private TestDataFactory() {}

    // ==================== Restaurant ====================

    public static Restaurant createRestaurant() {
        Restaurant r = new Restaurant();
        r.setId(1L);
        r.setName("Test Restaurant");
        r.setAddress("123 Test St");
        r.setCity("Tashkent");
        r.setPhone("+998901234567");
        r.setEmail("test@restaurant.com");
        r.setActive(true);
        r.setAcceptingOrders(true);
        r.setDeliveryFee(BigDecimal.valueOf(5.00));
        return r;
    }

    // ==================== RestaurantTable ====================

    public static RestaurantTable createTable() {
        return createTable(1L, "T1", TableStatus.AVAILABLE);
    }

    public static RestaurantTable createTable(Long id, String number, TableStatus status) {
        RestaurantTable t = new RestaurantTable();
        t.setId(id);
        t.setTableNumber(number);
        t.setTableName("Table " + number);
        t.setStatus(status);
        t.setCapacity(4);
        t.setActive(true);
        t.setRestaurant(createRestaurant());
        return t;
    }

    // ==================== Waiter ====================

    public static Waiter createWaiter() {
        return createWaiter(1L, "Test Waiter", "1234");
    }

    public static Waiter createWaiter(Long id, String name, String pin) {
        Waiter w = new Waiter();
        w.setId(id);
        w.setName(name);
        w.setPinCode(pin);
        w.setEmail(name.toLowerCase().replace(" ", ".") + "@test.com");
        w.setRole(WaiterRole.WAITER);
        w.setActive(true);
        w.setCommissionEnabled(false);
        w.setCommissionPercent(BigDecimal.ZERO);
        return w;
    }

    // ==================== Product ====================

    public static Product createProduct() {
        return createProduct(1L, "Test Product", BigDecimal.valueOf(10.00));
    }

    public static Product createProduct(Long id, String name, BigDecimal price) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(price);
        p.setInStock(true);
        return p;
    }

    public static ProductVariant createVariant(Long id, String name, BigDecimal price) {
        ProductVariant v = new ProductVariant();
        v.setId(id);
        v.setName(name);
        v.setPrice(price);
        return v;
    }

    // ==================== Customer ====================

    public static Customer createCustomer() {
        Customer c = new Customer();
        c.setId(1L);
        c.setRestaurantId(1L);
        c.setFirstName("Test");
        c.setLastName("Customer");
        c.setPhone("+998901111111");
        return c;
    }

    // ==================== Order ====================

    public static Order createOrder() {
        return createOrder(1L, OrderStatus.NEW);
    }

    public static Order createOrder(Long id, OrderStatus status) {
        Order o = Order.builder()
                .orderNumber("W" + System.currentTimeMillis())
                .restaurant(createRestaurant())
                .diningTable(createTable())
                .waiter(createWaiter())
                .status(status)
                .orderType(OrderType.DINE_IN)
                .orderSource(OrderSource.WAITER)
                .subtotal(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .build();
        o.setId(id);
        o.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        o.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return o;
    }

    // ==================== OrderItem ====================

    public static OrderItem createOrderItem(Long id, Long productId, String productName,
                                             int quantity, BigDecimal unitPrice) {
        return OrderItem.builder()
                .id(id)
                .productId(productId)
                .productName(productName)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .totalPrice(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                .build();
    }

    public static OrderItem createOrderItem(Long productId, String productName,
                                             int quantity, BigDecimal unitPrice) {
        return createOrderItem(null, productId, productName, quantity, unitPrice);
    }

    // ==================== DTOs ====================

    public static CreateOrderRequest createOrderRequest(Long tableId) {
        return createOrderRequest(tableId, null, List.of());
    }

    public static CreateOrderRequest createOrderRequest(Long tableId, Long customerId,
                                                         List<AddOrderItemRequest> items) {
        CreateOrderRequest r = new CreateOrderRequest();
        r.setTableId(tableId);
        r.setCustomerId(customerId);
        r.setGuestCount(2);
        r.setItems(items);
        return r;
    }

    public static AddOrderItemRequest createAddItemRequest(Long productId, int quantity) {
        AddOrderItemRequest r = new AddOrderItemRequest();
        r.setProductId(productId);
        r.setQuantity(quantity);
        return r;
    }

    public static AddOrderItemRequest createAddItemRequest(Long productId, Long variantId, int quantity) {
        AddOrderItemRequest r = createAddItemRequest(productId, quantity);
        r.setVariantId(variantId);
        return r;
    }

    public static UpdateOrderItemRequest createUpdateItemRequest(Integer quantity) {
        UpdateOrderItemRequest r = new UpdateOrderItemRequest();
        r.setQuantity(quantity);
        return r;
    }

    public static CreateWaiterRequest createWaiterRequest(String name, String pin) {
        CreateWaiterRequest r = new CreateWaiterRequest();
        r.setName(name);
        r.setPinCode(pin);
        r.setRole(WaiterRole.WAITER);
        r.setActive(true);
        return r;
    }
}

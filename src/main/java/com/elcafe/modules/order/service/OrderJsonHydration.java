package com.elcafe.modules.order.service;

import com.elcafe.modules.order.entity.Order;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Initializes the lazy state an {@link Order} needs to serialize its REST payload, so entity-returning
 * endpoints do not depend on {@code open-in-view} keeping a session alive during Jackson serialization.
 *
 * <p>Must run inside the loading transaction (call from {@code @Transactional} service methods, on
 * managed entities). What gets initialized and why:
 * <ul>
 *   <li>{@code items} + each item's {@code itemAddOns} — consumed by every order UI (add-ons by the
 *       by-shift report).</li>
 *   <li>{@code payments} — consumed by order history, and REQUIRED unconditionally: the serialized
 *       computed properties ({@code fullyPaid}, {@code payment}, {@code totalPaid},
 *       {@code remainingBalance}) iterate the collection inside their getters, where the Jackson
 *       Hibernate module cannot intercept — an uninitialized list there is a 500, not a null.</li>
 *   <li>{@code waiter}, {@code diningTable} — consumed by order history, by-shift, and the POS floor
 *       plan.</li>
 * </ul>
 * {@code orderTables} must be initialized even though no client reads it (frontend consumption audit,
 * 2026-07-10): the {@code tableIdList} computed getter iterates it. {@code customer},
 * {@code deliveryInfo}, {@code payments'} order back-references etc. are {@code @JsonIgnore}d and
 * never serialize. Initialization is idempotent and batched ({@code @BatchSize} /
 * {@code default_batch_fetch_size}), so hydrating a page costs a handful of queries, not N+1.
 */
public final class OrderJsonHydration {

    private OrderJsonHydration() {
    }

    public static Order forJson(Order order) {
        if (order == null) {
            return null;
        }
        Hibernate.initialize(order.getItems());
        if (Hibernate.isInitialized(order.getItems())) {
            order.getItems().forEach(item -> Hibernate.initialize(item.getItemAddOns()));
        }
        Hibernate.initialize(order.getPayments());
        // getTableIdList() iterates orderTables inside its getter — uninitialized, it aborts Jackson
        // mid-stream: the client gets a 200 with a truncated body that silently loses every property
        // serialized after it (fullyPaid, totalPaid, remainingBalance). Worse than a null field.
        Hibernate.initialize(order.getOrderTables());
        Hibernate.initialize(order.getWaiter());
        Hibernate.initialize(order.getDiningTable());
        return order;
    }

    public static List<Order> forJson(List<Order> orders) {
        orders.forEach(OrderJsonHydration::forJson);
        return orders;
    }

    public static Page<Order> forJson(Page<Order> orders) {
        orders.forEach(OrderJsonHydration::forJson);
        return orders;
    }
}

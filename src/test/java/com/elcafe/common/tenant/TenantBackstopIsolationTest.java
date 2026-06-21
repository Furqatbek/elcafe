package com.elcafe.common.tenant;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.notification.entity.Notification;
import com.elcafe.modules.notification.enums.NotificationType;
import com.elcafe.modules.notification.enums.UserRole;
import com.elcafe.modules.notification.repository.NotificationRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0 §3.4 — proves the Hibernate {@code restaurantFilter} backstop scopes real data when
 * enabled (as {@code TenantFilterInterceptor} does in {@code enforce} mode), and pins down exactly
 * which access paths it does and does not cover.
 *
 * <p>This is the gate for flipping {@code TENANT_ENFORCEMENT_MODE=enforce}: the existing
 * {@code TenantFilterInterceptorTest} only checks the gating logic (it stubs {@code enableFilter}),
 * so nothing verified the filter against real rows. It exercises a plain-column tenant entity
 * ({@code Customer}) and an FK-based one ({@code Order}).
 *
 * <p>Key result: <strong>queries are scoped, PK loads are not</strong>. A default Spring Data
 * {@code findById} uses {@code EntityManager.find} (a PK load the filter ignores), so it leaks across
 * tenants — which is why production routes repositories through {@link TenantScopedJpaRepository},
 * whose query-based {@code findById} <em>is</em> filtered (the mechanism is proven directly below).
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
@DisplayName("§3.4 tenant backstop — data-layer isolation")
class TenantBackstopIsolationTest {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private EntityManager em;

    private Long restaurantA;
    private Long restaurantB;
    private Long custA1Id;
    private Long custBId;
    private Long orderAId;
    private Long orderBId;
    private Long notifBId;

    @BeforeEach
    void setUp() {
        Restaurant a = restaurant("Tenant A");
        Restaurant b = restaurant("Tenant B");
        em.persist(a);
        em.persist(b);
        restaurantA = a.getId();
        restaurantB = b.getId();

        Customer custA1 = em.merge(customer(restaurantA, "Alice", "+99890A1"));
        em.persist(customer(restaurantA, "Aaron", "+99890A2"));
        Customer custB = em.merge(customer(restaurantB, "Bob", "+99890B1"));
        custA1Id = custA1.getId();
        custBId = custB.getId();

        Order orderA = order(a);
        Order orderB = order(b);
        em.persist(orderA);
        em.persist(orderB);
        orderAId = orderA.getId();
        orderBId = orderB.getId();

        Notification notifB = notification(restaurantB);
        em.persist(notification(restaurantA));
        em.persist(notifB);
        notifBId = notifB.getId();

        em.flush();
        em.clear(); // detach everything so reads hit the DB (and the filter), not the 1st-level cache
    }

    @AfterEach
    void disableFilter() {
        em.unwrap(Session.class).disableFilter("restaurantFilter");
    }

    private void enableFilterFor(Long tenantId) {
        em.unwrap(Session.class).enableFilter("restaurantFilter").setParameter("restaurantId", tenantId);
    }

    // --- Baseline: with no filter (shadow/off), nothing is scoped. ---

    @Test
    @DisplayName("no filter: both tenants' rows are visible (documents shadow/off behaviour)")
    void noFilter_seesEverything() {
        assertThat(customerRepository.findAll()).hasSize(3);
        assertThat(customerRepository.findById(custBId)).isPresent();
        assertThat(orderRepository.findById(orderBId)).isPresent();
    }

    // --- Query-based access (findAll, derived finders, JPQL) IS scoped by the filter. ---

    @Test
    @DisplayName("filter on: list/derived queries return only the bound tenant's rows")
    void filterOn_queriesAreScoped() {
        enableFilterFor(restaurantA);

        assertThat(customerRepository.findAll()).extracting(Customer::getRestaurantId)
                .containsOnly(restaurantA).hasSize(2);
        assertThat(orderRepository.findAll()).hasSize(1);

        // A derived finder for tenant B's phone finds nothing while scoped to A.
        assertThat(customerRepository.findByPhone("+99890B1")).isEmpty();
        // Same-tenant lookups still work (no false negatives / breakage).
        assertThat(customerRepository.findByPhone("+99890A1")).isPresent();
        assertThat(customerRepository.findByPhoneAndRestaurantId("+99890A1", restaurantA)).isPresent();
    }

    @Test
    @DisplayName("filter is parameterised: binding tenant B sees only B")
    void filterOn_isParameterised() {
        enableFilterFor(restaurantB);
        assertThat(customerRepository.findAll()).extracting(Customer::getRestaurantId)
                .containsOnly(restaurantB).hasSize(1);
        assertThat(orderRepository.findAll()).hasSize(1);
    }

    // --- findById: the raw gap vs the fix. Raw em.find (a PK load) is NOT scoped by the filter —
    // which is why repositories route through TenantScopedJpaRepository, whose query-based findById
    // IS scoped. Both are exercised here under the same enabled filter. ---

    @Test
    @DisplayName("GAP (why the fix exists): raw em.find PK load is NOT scoped")
    void rawEmFind_isNotScoped() {
        enableFilterFor(restaurantA);
        // Direct EntityManager.find bypasses the filter — the foreign tenant's row still loads.
        assertThat(em.find(Customer.class, custBId))
                .as("Hibernate @Filter does not apply to em.find PK loads")
                .isNotNull();
    }

    @Test
    @DisplayName("FIX: repository.findById (query-based, via TenantScopedJpaRepository) IS scoped")
    void repositoryFindById_isScoped() {
        enableFilterFor(restaurantA);

        // Same-tenant findById still works (no breakage / false negatives).
        assertThat(customerRepository.findById(custA1Id)).isPresent();

        // A foreign tenant's row is no longer reachable by surrogate id — closing the /{id} IDOR
        // class the §3.4 plan assumed @Filter already closed.
        assertThat(customerRepository.findById(custBId))
                .as("foreign Customer hidden via query-based findById")
                .isEmpty();
        assertThat(orderRepository.findById(orderBId))
                .as("same closure for FK-based entities")
                .isEmpty();
    }

    @Test
    @DisplayName("FIX extends to deleteById: a foreign id is not deleted while scoped")
    void repositoryDeleteById_isScoped() {
        enableFilterFor(restaurantA);

        // deleteById routes through the overridden (query-based) findById; a foreign id resolves to
        // empty, so tenant B's row is left untouched — closing the surrogate-id delete IDOR (e.g. the
        // blind allowance/schedule deleteById calls) on the flip. (Same-tenant delete still works.)
        customerRepository.deleteById(custBId);
        customerRepository.deleteById(custA1Id);
        em.flush();
        em.clear();

        em.unwrap(Session.class).disableFilter("restaurantFilter");
        assertThat(customerRepository.findById(custBId))
                .as("foreign row must not be deleted via scoped deleteById")
                .isPresent();
        assertThat(customerRepository.findById(custA1Id))
                .as("same-tenant delete still works")
                .isEmpty();
    }

    @Test
    @DisplayName("FIX covers notifications (§3.7 staff hardening): foreign-tenant notifications hidden")
    void filterOn_notificationsAreScoped() {
        enableFilterFor(restaurantA);

        // List + surrogate-id load both scope to the bound tenant — a staff member can no longer read
        // another restaurant's notifications by id or by passing its userId, and ADMIN "broadcasts"
        // stop fanning out across restaurants.
        assertThat(notificationRepository.findAll()).hasSize(1);
        assertThat(notificationRepository.findById(notifBId))
                .as("foreign notification hidden by the filter")
                .isEmpty();
    }

    private static Restaurant restaurant(String name) {
        Restaurant r = new Restaurant();
        r.setName(name);
        r.setAddress("addr");
        r.setActive(true);
        return r;
    }

    private static Customer customer(Long restaurantId, String firstName, String phone) {
        return Customer.builder().restaurantId(restaurantId)
                .firstName(firstName).lastName("X").phone(phone).active(true).build();
    }

    private static Order order(Restaurant r) {
        return Order.builder()
                .restaurant(r)
                .orderNumber("ORD-" + System.nanoTime())
                .status(OrderStatus.COMPLETED)
                .subtotal(BigDecimal.TEN).tax(BigDecimal.ZERO).discount(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO).total(BigDecimal.TEN)
                .build();
    }

    private static Notification notification(Long restaurantId) {
        return Notification.builder()
                .restaurantId(restaurantId)
                .userRole(UserRole.ADMIN)
                .type(NotificationType.NEW_ORDER)
                .title("t").message("m")
                .build();
    }
}

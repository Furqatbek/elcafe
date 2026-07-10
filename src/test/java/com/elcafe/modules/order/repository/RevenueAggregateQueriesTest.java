package com.elcafe.modules.order.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.order.dto.CouponSalesRow;
import com.elcafe.modules.order.dto.CustomerLifetimeRow;
import com.elcafe.modules.order.dto.CustomerOrderCountRow;
import com.elcafe.modules.order.dto.CustomerOrderStatsRow;
import com.elcafe.modules.order.dto.DiscountOrderRow;
import com.elcafe.modules.order.dto.HourlySalesRow;
import com.elcafe.modules.order.dto.OrderTimingRow;
import com.elcafe.modules.order.dto.OrderVolumeCountsRow;
import com.elcafe.modules.order.dto.PnlOrderRow;
import com.elcafe.modules.order.dto.ProductSalesRow;
import com.elcafe.modules.order.dto.RevenueOrderRow;
import com.elcafe.modules.order.dto.RevenueTotalsRow;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the semantics of the revenue-qualifying aggregate queries (audit PERF-2 rewrite) against a real
 * database: {@code REVENUE_QUALIFYING_WHERE} must include/exclude exactly the orders the old in-Java
 * filter did — every branch (revenue status; paymentStatus COMPLETED; fully-paid arithmetic with
 * grandTotal fallback, tips and refunds; CANCELLED and zero-total exclusions), plus tenant scoping,
 * range bounds, first-payment-method resolution, and null-productId (bundle row) exclusion.
 *
 * <p>Auditing (JpaConfig) is imported and timestamps key off "now": in a shared-JVM suite the auditing
 * listener from another cached context stamps entities regardless of this context's config, so manually
 * chosen createdAt values do not survive. The one order that must sit outside the range gets its
 * timestamp via native SQL after persist (bypasses listeners).
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class RevenueAggregateQueriesTest {

    private OffsetDateTime base;
    private OffsetDateTime start;
    private OffsetDateTime end;

    @Autowired private OrderRepository orderRepository;
    @Autowired private EntityManager entityManager;

    private Restaurant tenantA;
    private Restaurant tenantB;

    private Restaurant restaurant(String name) {
        Restaurant r = new Restaurant();
        r.setName(name);
        r.setAddress("1 Test St");
        r.setCity("Tashkent");
        r.setPhone("+998900000000");
        r.setEmail(name.toLowerCase() + "@test.com");
        r.setActive(true);
        r.setAcceptingOrders(true);
        r.setDeliveryFee(BigDecimal.ZERO);
        entityManager.persist(r);
        return r;
    }

    private Order order(Restaurant restaurant, String number, OrderStatus status, PaymentStatus paymentStatus,
                        String total, String grandTotal) {
        Order o = Order.builder()
                .orderNumber(number)
                .restaurant(restaurant)
                .status(status)
                .paymentStatus(paymentStatus)
                .orderType(OrderType.DINE_IN)
                .orderSource(OrderSource.WAITER)
                .subtotal(new BigDecimal(total))
                .deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(new BigDecimal(total))
                .grandTotal(grandTotal != null ? new BigDecimal(grandTotal) : null)
                .items(new ArrayList<>())
                .build();
        entityManager.persist(o);
        return o;
    }

    private Payment payment(Order order, PaymentMethod method, PaymentStatus status,
                            String amount, String tip, String refunded) {
        Payment p = Payment.builder()
                .order(order)
                .method(method)
                .status(status)
                .amount(new BigDecimal(amount))
                .tipAmount(new BigDecimal(tip))
                .refundedAmount(new BigDecimal(refunded))
                .build();
        entityManager.persist(p);
        entityManager.flush(); // fix identity order so "first payment" (MIN id) is deterministic
        return p;
    }

    private void item(Order order, Long productId, int quantity, String totalPrice) {
        OrderItem i = OrderItem.builder()
                .productId(productId)
                .productName(productId != null ? "Product " + productId : "Bundle row")
                .quantity(quantity)
                .unitPrice(new BigDecimal(totalPrice))
                .totalPrice(new BigDecimal(totalPrice))
                .build();
        order.addItem(i);
        entityManager.persist(i);
    }

    @BeforeEach
    void seed() {
        base = OffsetDateTime.now(ZoneOffset.UTC);
        start = base.minusDays(1);
        end = base.plusDays(1);

        tenantA = restaurant("TenantA");
        tenantB = restaurant("TenantB");

        // A customer for the customer-grouped aggregates (linked to T-REV and T-FULLPAID below)
        Customer customer = new Customer();
        customer.setRestaurantId(1L);
        customer.setFirstName("Ali");
        customer.setLastName("Tester");
        customer.setPhone("+998901112233");
        customer.setActive(true);
        entityManager.persist(customer);

        // Qualifies: revenue status (COMPLETED); one CASH payment → firstPaymentMethod CASH.
        // Carries a discount + coupon for the promotion aggregates.
        Order rev = order(tenantA, "T-REV", OrderStatus.COMPLETED, PaymentStatus.PENDING, "100", null);
        rev.setDiscount(new BigDecimal("10"));
        rev.setDiscountType("PROMO");
        rev.setCouponCode("SAVE10");
        rev.setCustomer(customer);
        payment(rev, PaymentMethod.CASH, PaymentStatus.COMPLETED, "100", "0", "0");
        item(rev, 1L, 2, "60");
        item(rev, 2L, 1, "40");

        // Qualifies via the qualifying filter's STATUS branch only (DELIVERED, unpaid) — the paid-only
        // filter must exclude it, the status-only filter must include it
        order(tenantA, "T-STATONLY", OrderStatus.DELIVERED, PaymentStatus.PENDING, "30", null);

        // Qualifies: paymentStatus COMPLETED (status NEW is not a revenue status); no payments → method null
        order(tenantA, "T-PAYSTAT", OrderStatus.NEW, PaymentStatus.COMPLETED, "50", null);

        // Qualifies: fully paid via two COMPLETED payments (50 CARD + 30 CASH ≥ total 80);
        // CARD persisted first → lower id → firstPaymentMethod CARD. Same customer as T-REV.
        Order fullPaid = order(tenantA, "T-FULLPAID", OrderStatus.NEW, PaymentStatus.PENDING, "80", null);
        fullPaid.setCustomer(customer);
        payment(fullPaid, PaymentMethod.CARD, PaymentStatus.COMPLETED, "50", "0", "0");
        payment(fullPaid, PaymentMethod.CASH, PaymentStatus.COMPLETED, "30", "0", "0");
        item(fullPaid, 1L, 3, "80");

        // Excluded: refund drops net paid (60 − 20 = 40) below total 60. Its items must not count.
        // Carries a coupon: the coupon aggregate does NOT require the order to be paid.
        Order refunded = order(tenantA, "T-REFUNDED", OrderStatus.NEW, PaymentStatus.PENDING, "60", null);
        refunded.setCouponCode("REF");
        payment(refunded, PaymentMethod.CARD, PaymentStatus.COMPLETED, "60", "0", "20");
        item(refunded, 1L, 99, "999");

        // Excluded: CANCELLED always loses, even with paymentStatus COMPLETED — and its coupon must not
        // show up in the coupon sums either
        Order cancelPaid = order(tenantA, "T-CANCELPAID", OrderStatus.CANCELLED, PaymentStatus.COMPLETED, "70", null);
        cancelPaid.setCouponCode("DEAD");

        // Excluded: zero effective total is never "fully paid", despite a COMPLETED payment
        Order zero = order(tenantA, "T-ZEROTOT", OrderStatus.NEW, PaymentStatus.PENDING, "0", null);
        payment(zero, PaymentMethod.CASH, PaymentStatus.COMPLETED, "10", "0", "0");

        // Qualifies: grandTotal (95) governs the fully-paid check, not total (10); revenue still sums total
        Order grand = order(tenantA, "T-GRAND", OrderStatus.NEW, PaymentStatus.PENDING, "10", "95");
        payment(grand, PaymentMethod.CASH, PaymentStatus.COMPLETED, "90", "5", "0"); // net 95 with tip
        item(grand, null, 1, "10"); // bundle/packaging row: null productId must be ignored

        // Excluded: only a PENDING payment → nothing completed
        Order pendPay = order(tenantA, "T-PENDPAY", OrderStatus.NEW, PaymentStatus.PENDING, "40", null);
        payment(pendPay, PaymentMethod.CASH, PaymentStatus.PENDING, "40", "0", "0");

        // Excluded from the range: qualifying by status but 10 days old. Auditing stamps createdAt=now on
        // persist, so push it back via native SQL (bypasses listeners and the updatable=false mapping).
        Order outOfRange = order(tenantA, "T-OUTRANGE", OrderStatus.COMPLETED, PaymentStatus.PENDING, "999", null);
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE orders SET created_at = :ts WHERE id = :id")
                .setParameter("ts", base.minusDays(10))
                .setParameter("id", outOfRange.getId())
                .executeUpdate();

        // Other tenant: excluded when filtering by tenant A, included for the all-tenants call
        order(tenantB, "T-OTHERTEN", OrderStatus.COMPLETED, PaymentStatus.PENDING, "77", null);

        entityManager.flush();
        entityManager.clear();
    }

    private RevenueTotalsRow totals(Long restaurantId) {
        return orderRepository.sumRevenueTotals(restaurantId, start, end,
                OrderStatus.CANCELLED, ShiftTimeService.REVENUE_STATUSES, PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("filter branches: exactly the four qualifying tenant-A orders count toward totals")
    void totalsFilterBranches() {
        RevenueTotalsRow rowA = totals(tenantA.getId());
        // 100 (revenue status) + 30 (status-only) + 50 (paymentStatus) + 80 (fully paid) + 10 (grandTotal-paid)
        assertThat(rowA.totalRevenue()).isEqualByComparingTo("270");
        assertThat(rowA.orderCount()).isEqualTo(5);

        RevenueTotalsRow all = totals(null);
        assertThat(all.totalRevenue()).isEqualByComparingTo("347"); // + tenant B's 77
        assertThat(all.orderCount()).isEqualTo(6);

        RevenueTotalsRow empty = orderRepository.sumRevenueTotals(tenantA.getId(),
                base.minusYears(5), base.minusYears(4),
                OrderStatus.CANCELLED, ShiftTimeService.REVENUE_STATUSES, PaymentStatus.COMPLETED);
        assertThat(empty.totalRevenue()).isEqualByComparingTo("0"); // COALESCE, not null
        assertThat(empty.orderCount()).isZero();
    }

    @Test
    @DisplayName("per-order rows: one row per qualifying order with the first payment's method (or null)")
    void revenueOrderRows() {
        List<RevenueOrderRow> rows = orderRepository.findRevenueOrderRows(tenantA.getId(), start, end,
                OrderStatus.CANCELLED, ShiftTimeService.REVENUE_STATUSES, PaymentStatus.COMPLETED);

        // Not Collectors.toMap — it rejects null values, and the no-payment order's method IS null
        Map<String, PaymentMethod> byTotal = new java.util.HashMap<>();
        rows.forEach(r -> byTotal.put(r.total().stripTrailingZeros().toPlainString(), r.firstPaymentMethod()));

        assertThat(rows).hasSize(5);
        assertThat(byTotal)
                .containsEntry("100", PaymentMethod.CASH)   // single payment
                .containsEntry("80", PaymentMethod.CARD)    // first (lowest-id) of two payments
                .containsEntry("10", PaymentMethod.CASH);   // grandTotal-qualified order
        assertThat(byTotal).containsKey("50");
        assertThat(byTotal.get("50")).isNull();             // no payments at all
        assertThat(byTotal).containsKey("30");
        assertThat(byTotal.get("30")).isNull();             // status-only order, unpaid
        assertThat(rows).allSatisfy(r -> assertThat(r.createdAt()).isNotNull());
    }

    @Test
    @DisplayName("product sales: DB-side per-product sums over qualifying orders only, null productId excluded")
    void productSales() {
        List<ProductSalesRow> rows = orderRepository.sumProductSales(tenantA.getId(), start, end,
                OrderStatus.CANCELLED, ShiftTimeService.REVENUE_STATUSES, PaymentStatus.COMPLETED);

        Map<Long, ProductSalesRow> byProduct = rows.stream()
                .collect(Collectors.toMap(ProductSalesRow::productId, r -> r));

        // product 1: 60 (T-REV) + 80 (T-FULLPAID); the non-qualifying T-REFUNDED's 999/99 must be absent
        assertThat(byProduct).containsOnlyKeys(1L, 2L);
        assertThat(byProduct.get(1L).revenue()).isEqualByComparingTo("140");
        assertThat(byProduct.get(1L).quantitySold()).isEqualTo(5);
        assertThat(byProduct.get(2L).revenue()).isEqualByComparingTo("40");
        assertThat(byProduct.get(2L).quantitySold()).isEqualTo(1);
    }

    @Test
    @DisplayName("status-only aggregates: hourly sales, dine-in count, per-product sums, volume counts")
    void statusOnlyAggregates() {
        // Only T-REV (COMPLETED, 100) and T-STATONLY (DELIVERED, 30) carry a revenue status
        List<HourlySalesRow> hourly = orderRepository.findHourlySales(
                tenantA.getId(), start, end, ShiftTimeService.REVENUE_STATUSES);
        assertThat(hourly).hasSize(1); // both orders share the seeding hour
        assertThat(hourly.get(0).revenue()).isEqualByComparingTo("130");
        assertThat(hourly.get(0).orderCount()).isEqualTo(2);
        assertThat(hourly.get(0).hour()).isBetween(0, 23);

        // No DeliveryInfo rows are seeded → every revenue-status order counts as dine-in
        long dineIn = orderRepository.countDineInRevenueOrders(
                tenantA.getId(), start, end, ShiftTimeService.REVENUE_STATUSES);
        assertThat(dineIn).isEqualTo(2);

        // The status-only product sums see ONLY T-REV's items (T-FULLPAID is NEW → excluded here,
        // unlike in the qualifying-filter variant)
        List<ProductSalesRow> byStatus = orderRepository.sumProductSalesByStatus(
                tenantA.getId(), start, end, ShiftTimeService.REVENUE_STATUSES);
        Map<Long, ProductSalesRow> byProduct = byStatus.stream()
                .collect(Collectors.toMap(ProductSalesRow::productId, r -> r));
        assertThat(byProduct).containsOnlyKeys(1L, 2L);
        assertThat(byProduct.get(1L).revenue()).isEqualByComparingTo("60");
        assertThat(byProduct.get(1L).quantitySold()).isEqualTo(2);

        // Volumes over ALL statuses: 9 in-range tenant-A orders, 2 revenue-status, 1 cancelled
        OrderVolumeCountsRow volumes = orderRepository.countOrderVolumes(
                tenantA.getId(), start, end, ShiftTimeService.REVENUE_STATUSES, OrderStatus.CANCELLED);
        assertThat(volumes.totalOrders()).isEqualTo(9);
        assertThat(volumes.completedOrders()).isEqualTo(2);
        assertThat(volumes.cancelledOrders()).isEqualTo(1);
    }

    @Test
    @DisplayName("paid-only rows: fully-paid/paymentStatus orders in, status-only and refunded orders out")
    void paidOnlyRows() {
        List<DiscountOrderRow> rows = orderRepository.findPaidDiscountOrderRows(
                tenantA.getId(), start, end, OrderStatus.CANCELLED, PaymentStatus.COMPLETED);

        // T-REV (fully paid, discount 10/PROMO), T-PAYSTAT (paymentStatus), T-FULLPAID, T-GRAND —
        // but NOT T-STATONLY (revenue status alone doesn't pay the bill) and NOT T-REFUNDED
        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(r -> r.total().stripTrailingZeros().toPlainString())
                .containsExactlyInAnyOrder("100", "50", "80", "10");
        DiscountOrderRow revRow = rows.stream()
                .filter(r -> r.total().compareTo(new BigDecimal("100")) == 0).findFirst().orElseThrow();
        assertThat(revRow.discount()).isEqualByComparingTo("10");
        assertThat(revRow.discountType()).isEqualTo("PROMO");
    }

    @Test
    @DisplayName("coupon sums: grouped per code over non-cancelled orders, paid or not; cancelled excluded")
    void couponSums() {
        List<CouponSalesRow> rows = orderRepository.sumCouponSales(
                tenantA.getId(), start, end, OrderStatus.CANCELLED);

        Map<String, CouponSalesRow> byCode = rows.stream()
                .collect(Collectors.toMap(CouponSalesRow::couponCode, r -> r));
        // SAVE10 from T-REV; REF from the unpaid T-REFUNDED (coupon stats don't require payment);
        // DEAD from T-CANCELPAID must be absent
        assertThat(byCode).containsOnlyKeys("SAVE10", "REF");
        assertThat(byCode.get("SAVE10").redemptionCount()).isEqualTo(1);
        assertThat(byCode.get("SAVE10").revenue()).isEqualByComparingTo("100");
        assertThat(byCode.get("SAVE10").discount()).isEqualByComparingTo("10");
        assertThat(byCode.get("REF").revenue()).isEqualByComparingTo("60");
        assertThat(byCode.get("REF").discount()).isEqualByComparingTo("0"); // null discount coalesces
    }

    @Test
    @DisplayName("customer aggregates: per-range stats, all-status counts, and lifetime rows")
    void customerAggregates() {
        // Revenue-STATUS orders with a customer in range: only T-REV → count 1
        List<CustomerOrderStatsRow> stats = orderRepository.findCustomerOrderStats(
                tenantA.getId(), start, end, ShiftTimeService.REVENUE_STATUSES);
        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).orderCount()).isEqualTo(1);
        assertThat(stats.get(0).customerCreatedAt()).isNotNull();

        // ALL orders with a customer in range: T-REV + T-FULLPAID → count 2
        List<CustomerOrderCountRow> counts = orderRepository.findCustomerOrderCounts(
                tenantA.getId(), start, end);
        assertThat(counts).hasSize(1);
        assertThat(counts.get(0).orderCount()).isEqualTo(2);

        // Lifetime rows (no range): active customer, revenue-status orders only → T-REV
        List<CustomerLifetimeRow> lifetime = orderRepository.findCustomerLifetimeStats(
                tenantA.getId(), ShiftTimeService.REVENUE_STATUSES);
        assertThat(lifetime).hasSize(1);
        assertThat(lifetime.get(0).totalSpent()).isEqualByComparingTo("100");
        assertThat(lifetime.get(0).orderCount()).isEqualTo(1);
        assertThat(lifetime.get(0).firstOrderAt()).isEqualTo(lifetime.get(0).lastOrderAt());
    }

    @Test
    @DisplayName("timing and P&L rows project scalars for the right order sets")
    void timingAndPnlRows() {
        // Timing rows follow the status-only filter; no history/kitchen/delivery rows are seeded,
        // so the fallback-relevant fields must come back empty/zero
        List<OrderTimingRow> timing = orderRepository.findOrderTimingRows(
                tenantA.getId(), start, end, ShiftTimeService.REVENUE_STATUSES,
                OrderStatus.NEW, OrderStatus.READY, OrderStatus.DELIVERED);
        assertThat(timing).hasSize(2);
        assertThat(timing).allSatisfy(row -> {
            assertThat(row.historyCount()).isZero();
            assertThat(row.kitchenPrepMinutes()).isNull();
            assertThat(row.deliveryInfoId()).isNull();
            assertThat(row.newAt()).isNull();
            assertThat(row.createdAt()).isNotNull();
            assertThat(row.updatedAt()).isNotNull();
        });

        // P&L rows follow the qualifying filter (5 orders); spot-check T-REV's money fields
        List<PnlOrderRow> pnl = orderRepository.findPnlOrderRows(
                tenantA.getId(), start, end,
                OrderStatus.CANCELLED, ShiftTimeService.REVENUE_STATUSES, PaymentStatus.COMPLETED);
        assertThat(pnl).hasSize(5);
        PnlOrderRow revRow = pnl.stream()
                .filter(r -> r.total().compareTo(new BigDecimal("100")) == 0).findFirst().orElseThrow();
        assertThat(revRow.subtotal()).isEqualByComparingTo("100");
        assertThat(revRow.discount()).isEqualByComparingTo("10");
        assertThat(revRow.discountType()).isEqualTo("PROMO");
    }
}

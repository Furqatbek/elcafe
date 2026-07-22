package com.elcafe.modules.financial.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.financial.entity.PurchaseOrder;
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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class PurchaseOrderRepositoryTest {

    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);
    }

    private PurchaseOrder createPurchaseOrder(String poNumber, String supplierName,
                                              LocalDate orderDate, PurchaseOrder.Status status,
                                              PurchaseOrder.PaymentStatus paymentStatus,
                                              BigDecimal totalAmount) {
        PurchaseOrder po = PurchaseOrder.builder()
                .restaurant(restaurant)
                .poNumber(poNumber)
                .supplierName(supplierName)
                .orderDate(orderDate)
                .status(status)
                .paymentStatus(paymentStatus)
                .totalAmount(totalAmount)
                .subtotal(totalAmount)
                .build();
        em.persist(po);
        return po;
    }

    @Test
    @DisplayName("findUnpaidOrders returns orders where paymentStatus != PAID")
    void findUnpaidOrders() {
        createPurchaseOrder("PO-001", "Supplier A", LocalDate.of(2025, 1, 10),
                PurchaseOrder.Status.ORDERED, PurchaseOrder.PaymentStatus.UNPAID, new BigDecimal("500.00"));
        createPurchaseOrder("PO-002", "Supplier B", LocalDate.of(2025, 1, 15),
                PurchaseOrder.Status.RECEIVED, PurchaseOrder.PaymentStatus.PARTIALLY_PAID, new BigDecimal("800.00"));
        createPurchaseOrder("PO-003", "Supplier C", LocalDate.of(2025, 1, 20),
                PurchaseOrder.Status.RECEIVED, PurchaseOrder.PaymentStatus.PAID, new BigDecimal("300.00"));

        em.flush();
        em.clear();

        List<PurchaseOrder> results = purchaseOrderRepository.findUnpaidOrders(restaurant.getId());
        assertEquals(2, results.size());
        assertTrue(results.stream().noneMatch(po -> po.getPaymentStatus() == PurchaseOrder.PaymentStatus.PAID));
    }

    @Test
    @DisplayName("findReceivedButUnpaid returns orders with status=RECEIVED and paymentStatus != PAID")
    void findReceivedButUnpaid() {
        createPurchaseOrder("PO-001", "Supplier A", LocalDate.of(2025, 1, 10),
                PurchaseOrder.Status.ORDERED, PurchaseOrder.PaymentStatus.UNPAID, new BigDecimal("500.00"));
        PurchaseOrder received1 = createPurchaseOrder("PO-002", "Supplier B", LocalDate.of(2025, 1, 15),
                PurchaseOrder.Status.RECEIVED, PurchaseOrder.PaymentStatus.UNPAID, new BigDecimal("800.00"));
        received1.setActualDeliveryDate(LocalDate.of(2025, 1, 18));
        PurchaseOrder received2 = createPurchaseOrder("PO-003", "Supplier C", LocalDate.of(2025, 1, 12),
                PurchaseOrder.Status.RECEIVED, PurchaseOrder.PaymentStatus.PARTIALLY_PAID, new BigDecimal("600.00"));
        received2.setActualDeliveryDate(LocalDate.of(2025, 1, 14));
        createPurchaseOrder("PO-004", "Supplier D", LocalDate.of(2025, 1, 20),
                PurchaseOrder.Status.RECEIVED, PurchaseOrder.PaymentStatus.PAID, new BigDecimal("300.00"));

        em.flush();
        em.clear();

        List<PurchaseOrder> results = purchaseOrderRepository.findReceivedButUnpaid(restaurant.getId());
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(po ->
                po.getStatus() == PurchaseOrder.Status.RECEIVED
                        && po.getPaymentStatus() != PurchaseOrder.PaymentStatus.PAID));
        // Verify ASC ordering by actualDeliveryDate
        assertTrue(results.get(0).getActualDeliveryDate().isBefore(results.get(1).getActualDeliveryDate())
                || results.get(0).getActualDeliveryDate().isEqual(results.get(1).getActualDeliveryDate()));
    }
}

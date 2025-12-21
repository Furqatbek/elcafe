package com.elcafe.modules.financial.repository;

import com.elcafe.modules.financial.entity.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    List<PurchaseOrder> findByRestaurantId(Long restaurantId);

    Page<PurchaseOrder> findByRestaurantId(Long restaurantId, Pageable pageable);

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    List<PurchaseOrder> findByRestaurantIdAndStatus(Long restaurantId, PurchaseOrder.Status status);

    List<PurchaseOrder> findByRestaurantIdAndStatusIn(Long restaurantId, List<PurchaseOrder.Status> statuses);

    List<PurchaseOrder> findByRestaurantIdAndPaymentStatus(Long restaurantId, PurchaseOrder.PaymentStatus paymentStatus);

    List<PurchaseOrder> findByRestaurantIdAndSupplierName(Long restaurantId, String supplierName);

    List<PurchaseOrder> findByRestaurantIdAndOrderDateBetween(
            Long restaurantId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT po FROM FinancialPurchaseOrder po WHERE po.restaurant.id = :restaurantId " +
           "AND po.status IN ('APPROVED', 'ORDERED') " +
           "AND po.expectedDeliveryDate < :date")
    List<PurchaseOrder> findOverduePurchaseOrders(Long restaurantId, LocalDate date);

    @Query("SELECT po FROM FinancialPurchaseOrder po WHERE po.restaurant.id = :restaurantId " +
           "AND po.paymentStatus != 'PAID' " +
           "ORDER BY po.orderDate DESC")
    List<PurchaseOrder> findUnpaidOrders(Long restaurantId);

    @Query("SELECT po FROM FinancialPurchaseOrder po WHERE po.restaurant.id = :restaurantId " +
           "AND po.status = 'RECEIVED' AND po.paymentStatus != 'PAID' " +
           "ORDER BY po.actualDeliveryDate ASC")
    List<PurchaseOrder> findReceivedButUnpaid(Long restaurantId);
}

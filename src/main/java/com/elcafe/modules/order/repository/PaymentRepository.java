package com.elcafe.modules.order.repository;

import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Payment entity - supports multiple payments per order
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Multi-payment queries
    List<Payment> findByOrderId(Long orderId);

    List<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    Optional<Payment> findByIdAndOrderId(Long id, Long orderId);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    Page<Payment> findByMethod(PaymentMethod method, Pageable pageable);

    Optional<Payment> findByTransactionId(String transactionId);

    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.createdAt BETWEEN :startDate AND :endDate")
    List<Payment> findByStatusAndDateRange(
            @Param("status") PaymentStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT p FROM Payment p WHERE p.method = :method AND p.createdAt BETWEEN :startDate AND :endDate")
    List<Payment> findByMethodAndDateRange(
            @Param("method") PaymentMethod method,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = :status")
    long countByStatus(@Param("status") PaymentStatus status);

    boolean existsByTransactionId(String transactionId);

    // Aggregation queries for split payments
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.order.id = :orderId AND p.status = 'COMPLETED'")
    BigDecimal sumCompletedPaymentsByOrderId(@Param("orderId") Long orderId);

    @Query("SELECT COALESCE(SUM(p.tipAmount), 0) FROM Payment p WHERE p.order.id = :orderId AND p.status = 'COMPLETED'")
    BigDecimal sumTipsByOrderId(@Param("orderId") Long orderId);

    @Query("SELECT COALESCE(SUM(p.refundedAmount), 0) FROM Payment p WHERE p.order.id = :orderId")
    BigDecimal sumRefundsByOrderId(@Param("orderId") Long orderId);

    // Check if order has any completed payments
    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
}

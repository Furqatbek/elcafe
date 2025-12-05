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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Payment entity
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

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
}

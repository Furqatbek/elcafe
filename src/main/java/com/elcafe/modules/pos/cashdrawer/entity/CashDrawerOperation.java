package com.elcafe.modules.pos.cashdrawer.entity;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.pos.cashdrawer.enums.CashOperationType;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Records all cash drawer operations for audit purposes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cash_drawer_operations", indexes = {
    @Index(name = "idx_cash_drawer_ops_drawer", columnList = "cash_drawer_id"),
    @Index(name = "idx_cash_drawer_ops_shift", columnList = "shift_id"),
    @Index(name = "idx_cash_drawer_ops_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CashDrawerOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_drawer_id", nullable = false)
    private CashDrawer cashDrawer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private EmployeeShift shift;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 50)
    private CashOperationType operationType;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id", nullable = false)
    private User operator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;
}

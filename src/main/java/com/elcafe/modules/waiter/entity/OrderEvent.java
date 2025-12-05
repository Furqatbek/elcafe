package com.elcafe.modules.waiter.entity;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.waiter.enums.OrderEventType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Audit trail for all order-related events
 * Tracks every action taken on an order for accountability and debugging
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@jakarta.persistence.Table(name = "order_events", indexes = {
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OrderEventType eventType;

    /**
     * Who triggered this event (waiter name, system, kitchen staff, etc.)
     */
    @Column(length = 100)
    private String triggeredBy;

    /**
     * Additional data about the event in JSON format
     * Example: {"itemId": 123, "reason": "customer request", "previousStatus": "COOKING"}
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

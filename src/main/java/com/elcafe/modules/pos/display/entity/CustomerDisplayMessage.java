package com.elcafe.modules.pos.display.entity;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.pos.display.enums.DisplayMessageType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Queue of messages to display on customer-facing display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "customer_display_messages", indexes = {
    @Index(name = "idx_cust_display_msgs_display", columnList = "customer_display_id"),
    @Index(name = "idx_cust_display_msgs_order", columnList = "order_id")
})
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CustomerDisplayMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_display_id", nullable = false)
    private CustomerDisplay customerDisplay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 30)
    private DisplayMessageType messageType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "message_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> messageData;

    @Column(name = "displayed_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime displayedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    public void markDisplayed() {
        this.displayedAt = OffsetDateTime.now();
    }
}

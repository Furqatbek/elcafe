package com.elcafe.modules.selfservice.entity;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.selfservice.enums.SelfServiceOrderType;
import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Self-service order metadata linked to main order.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "self_service_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelfServiceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private SelfServiceSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qr_code_id")
    private QRCode qrCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 50)
    @Builder.Default
    private SelfServiceOrderType orderType = SelfServiceOrderType.DINE_IN;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Column(name = "special_instructions", columnDefinition = "TEXT")
    private String specialInstructions;

    @Column(name = "estimated_ready_time")
    private LocalDateTime estimatedReadyTime;

    @Column(name = "actual_ready_time")
    private LocalDateTime actualReadyTime;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "feedback_rating")
    private Integer feedbackRating;

    @Column(name = "feedback_comment", columnDefinition = "TEXT")
    private String feedbackComment;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Mark order as ready and set ready time.
     */
    public void markReady() {
        this.actualReadyTime = LocalDateTime.now();
    }

    /**
     * Mark order as notified.
     */
    public void markNotified() {
        this.notifiedAt = LocalDateTime.now();
    }

    /**
     * Mark order as picked up.
     */
    public void markPickedUp() {
        this.pickedUpAt = LocalDateTime.now();
    }

    /**
     * Add feedback to the order.
     */
    public void addFeedback(Integer rating, String comment) {
        this.feedbackRating = rating;
        this.feedbackComment = comment;
    }
}

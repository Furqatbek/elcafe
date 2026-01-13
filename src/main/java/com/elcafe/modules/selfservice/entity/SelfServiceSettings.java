package com.elcafe.modules.selfservice.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Self-service settings for a restaurant.
 */
@Entity
@Table(name = "self_service_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelfServiceSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false, unique = true)
    private Restaurant restaurant;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @Column(name = "require_payment")
    @Builder.Default
    private Boolean requirePayment = false;

    @Column(name = "allow_takeaway")
    @Builder.Default
    private Boolean allowTakeaway = true;

    @Column(name = "allow_dine_in")
    @Builder.Default
    private Boolean allowDineIn = true;

    @Column(name = "minimum_order_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal minimumOrderAmount = BigDecimal.ZERO;

    @Column(name = "service_charge_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal serviceChargePercent = BigDecimal.ZERO;

    @Column(name = "auto_accept_orders")
    @Builder.Default
    private Boolean autoAcceptOrders = false;

    @Column(name = "estimated_prep_time_minutes")
    @Builder.Default
    private Integer estimatedPrepTimeMinutes = 15;

    @Column(name = "show_wait_time")
    @Builder.Default
    private Boolean showWaitTime = true;

    @Column(name = "allow_special_instructions")
    @Builder.Default
    private Boolean allowSpecialInstructions = true;

    @Column(name = "max_items_per_order")
    @Builder.Default
    private Integer maxItemsPerOrder = 50;

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
}

package com.elcafe.modules.restaurant.entity;

import com.elcafe.modules.billing.entity.SubscriptionPlan;
import com.elcafe.modules.billing.enums.SubscriptionStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "restaurants")
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
// Phase 0 §3.4: global definition of the tenant-isolation filter. Applied (via @Filter) to every
// restaurant-scoped entity and enabled per-request by TenantFilterInterceptor in enforce mode.
@FilterDef(name = "restaurantFilter", parameters = @ParamDef(name = "restaurantId", type = Long.class))
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 500)
    private String logoUrl;

    @Column(length = 500)
    private String bannerUrl;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 20)
    private String zipCode;

    @Column(length = 100)
    private String country;

    private Double latitude;

    private Double longitude;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(length = 500)
    private String website;

    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal rating = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean acceptingOrders = true;

    private BigDecimal minimumOrderAmount;

    private BigDecimal deliveryFee;

    private Integer estimatedDeliveryTimeMinutes;

    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore  // Prevent lazy loading issues when serializing orders
    private List<BusinessHours> businessHours = new ArrayList<>();

    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore  // Prevent lazy loading issues when serializing orders
    private List<DeliveryZone> deliveryZones = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Phase 1 (subscription tiers) mini-phase A1 — plan attachment. The DB enforces plan_id NOT NULL
    // after backfill (V156); the JPA relation is intentionally left nullable so code/tests that build
    // a Restaurant before plan assignment are unaffected (Hibernate `validate` ignores nullability).
    // @JsonIgnore keeps the lazy association out of existing Restaurant payloads — the billing API
    // exposes plan info via its own DTO (mini-phase A2/A3), not the raw entity.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    @JsonIgnore
    private SubscriptionPlan plan;

    @Column(name = "plan_started_at")
    private LocalDateTime planStartedAt;

    /** When the current plan lapses. NULL = no expiry (free Start tier). */
    @Column(name = "plan_expires_at")
    private LocalDateTime planExpiresAt;

    @Column(name = "is_trial", nullable = false)
    @Builder.Default
    private Boolean isTrial = false;

    // Phase 3 scaffolding: the formal lifecycle state, reconciled from plan/expiry/active by
    // BillingService (CANCELLED is sticky). Derived today; the billing engine will drive PAST_DUE.
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.ACTIVE;

    public void addBusinessHours(BusinessHours hours) {
        businessHours.add(hours);
        hours.setRestaurant(this);
    }

    public void addDeliveryZone(DeliveryZone zone) {
        deliveryZones.add(zone);
        zone.setRestaurant(this);
    }
}

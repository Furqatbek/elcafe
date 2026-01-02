package com.elcafe.modules.inventory.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "suppliers",
       uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id", "code"}))
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String code;

    // Contact Information
    @Column(name = "contact_person", length = 100)
    private String contactPerson;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    // Business Terms
    @Column(name = "payment_terms", length = 50)
    private String paymentTerms;

    @Column(name = "credit_limit", precision = 12, scale = 2)
    private BigDecimal creditLimit;

    @Column(length = 3)
    @Builder.Default
    private String currency = "UZS";

    // Performance Metrics
    @Column(precision = 2, scale = 1)
    @Builder.Default
    private BigDecimal rating = BigDecimal.ZERO;

    @Column(name = "total_orders")
    @Builder.Default
    private Integer totalOrders = 0;

    @Column(name = "on_time_delivery_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal onTimeDeliveryRate = BigDecimal.ZERO;

    // Status
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

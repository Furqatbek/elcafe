package com.elcafe.modules.menu.entity;

import com.elcafe.modules.menu.enums.QuantityMode;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "packaging_rules", indexes = {
        @Index(name = "idx_packaging_rules_product", columnList = "product_id"),
        @Index(name = "idx_packaging_rules_restaurant", columnList = "restaurant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackagingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "packaging_product_id", nullable = false)
    private Product packagingProduct;

    @Column(name = "order_types", nullable = false, length = 50)
    @Builder.Default
    private String orderTypes = "DELIVERY,TAKEAWAY";

    @Enumerated(EnumType.STRING)
    @Column(name = "quantity_mode", nullable = false, length = 20)
    @Builder.Default
    private QuantityMode quantityMode = QuantityMode.PER_ITEM;

    @Column(name = "auto_add_quantity", nullable = false)
    @Builder.Default
    private Integer autoAddQuantity = 1;

    @Column(name = "charge_to_customer", nullable = false)
    @Builder.Default
    private Boolean chargeToCustomer = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public boolean appliesToOrderType(String orderType) {
        if (orderTypes == null || orderType == null) return false;
        return orderTypes.contains(orderType);
    }
}

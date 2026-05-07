package com.elcafe.modules.promotion.entity;

import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.promotion.enums.PromotionScope;
import com.elcafe.modules.promotion.enums.PromotionType;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
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
@ToString(exclude = {"rule", "promotionProducts", "couponCodes", "restaurant", "freeProduct"})
@EqualsAndHashCode(exclude = {"rule", "promotionProducts", "couponCodes", "restaurant", "freeProduct"})
@Entity
@Table(name = "promotions")
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    @JsonIgnore
    private Restaurant restaurant;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "promotion_type", nullable = false)
    private PromotionType promotionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "promotion_scope", nullable = false)
    @Builder.Default
    private PromotionScope promotionScope = PromotionScope.ALL;

    @Column(name = "discount_value", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal discountValue = BigDecimal.ZERO;

    @Column(name = "buy_quantity")
    private Integer buyQuantity;

    @Column(name = "get_quantity")
    private Integer getQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "free_product_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "variants", "ingredients", "linkedItems", "addOnGroups", "category"})
    private Product freeProduct;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean stackable = false;

    @OneToOne(mappedBy = "promotion", cascade = CascadeType.ALL, orphanRemoval = true)
    private PromotionRule rule;

    @OneToMany(mappedBy = "promotion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PromotionProduct> promotionProducts = new ArrayList<>();

    @OneToMany(mappedBy = "promotion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<CouponCode> couponCodes = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void setRule(PromotionRule rule) {
        this.rule = rule;
        if (rule != null) {
            rule.setPromotion(this);
        }
    }

    public void addPromotionProduct(PromotionProduct promotionProduct) {
        promotionProducts.add(promotionProduct);
        promotionProduct.setPromotion(this);
    }

    public void addCouponCode(CouponCode couponCode) {
        couponCodes.add(couponCode);
        couponCode.setPromotion(this);
    }

    /**
     * Check if the promotion is currently valid (active and within date range)
     */
    public boolean isValid() {
        if (!active) return false;
        LocalDateTime now = LocalDateTime.now();
        if (startDate != null && now.isBefore(startDate)) return false;
        if (endDate != null && now.isAfter(endDate)) return false;
        return true;
    }
}

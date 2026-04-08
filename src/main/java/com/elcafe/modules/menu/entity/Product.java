package com.elcafe.modules.menu.entity;

import com.elcafe.modules.menu.enums.ItemType;
import com.elcafe.modules.menu.enums.ProductStatus;
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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "products")
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"category", "variants", "ingredients", "linkedItems", "addOnGroups", "hibernateLazyInitializer", "handler"})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "price_with_margin", precision = 10, scale = 2)
    private BigDecimal priceWithMargin;

    @Column(name = "cost_price", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "margin_percentage", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal marginPercentage = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", length = 100)
    private ItemType itemType;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(nullable = false)
    @Builder.Default
    private Boolean inStock = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean featured = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean hasVariants = false;

    // Barcode/SKU fields
    @Column(length = 100)
    private String sku;

    @Column(length = 100)
    private String barcode;

    @Column(name = "barcode_type", length = 20)
    private String barcodeType; // UPC, EAN13, EAN8, CODE128, CODE39

    // Weight-based pricing fields (for sell-by-weight items)
    @Column(name = "is_sold_by_weight")
    @Builder.Default
    private Boolean isSoldByWeight = false;

    @Column(name = "weight_unit", length = 10)
    private String weightUnit; // KG, LB, OZ, G

    @Column(name = "tare_weight", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal tareWeight = BigDecimal.ZERO; // Container weight

    @Column(name = "min_weight", precision = 10, scale = 4)
    private BigDecimal minWeight;

    @Column(name = "max_weight", precision = 10, scale = 4)
    private BigDecimal maxWeight;

    @Column(name = "is_available")
    @Builder.Default
    private Boolean isAvailable = true;

    @Column(name = "uses_production_batch")
    @Builder.Default
    private Boolean usesProductionBatch = false;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductIngredient> ingredients = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LinkedItem> linkedItems = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "product_addon_groups",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "addon_group_id")
    )
    @Builder.Default
    private List<AddOnGroup> addOnGroups = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void addVariant(ProductVariant variant) {
        variants.add(variant);
        variant.setProduct(this);
    }

    public void addIngredient(ProductIngredient ingredient) {
        ingredients.add(ingredient);
        ingredient.setProduct(this);
    }

    public void addLinkedItem(LinkedItem linkedItem) {
        linkedItems.add(linkedItem);
        linkedItem.setProduct(this);
    }

    /**
     * Calculate total cost based on ingredients
     */
    public BigDecimal calculateCostPrice() {
        return ingredients.stream()
                .map(ProductIngredient::calculateCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate profit margin percentage
     */
    public BigDecimal calculateProfitMargin() {
        if (price == null || costPrice == null || costPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return price.subtract(costPrice)
                .divide(costPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}

package com.elcafe.modules.bundle.entity;

import com.elcafe.modules.menu.entity.Product;
import jakarta.persistence.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bundle_items", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"bundle_id", "product_id"})
})
public class BundleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bundle_id", nullable = false)
    @ToString.Exclude
    private Bundle bundle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(name = "is_required")
    @Builder.Default
    private Boolean isRequired = true;

    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = true;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;
}

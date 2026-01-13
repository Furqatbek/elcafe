package com.elcafe.modules.selfservice.entity;

import com.elcafe.modules.menu.entity.LinkedItem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Modifier (add-on) for a cart item.
 */
@Entity
@Table(name = "self_service_cart_modifiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelfServiceCartModifier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_item_id", nullable = false)
    private SelfServiceCartItem cartItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_item_id", nullable = false)
    private LinkedItem linkedItem;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
}

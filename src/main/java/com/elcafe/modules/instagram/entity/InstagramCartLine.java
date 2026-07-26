package com.elcafe.modules.instagram.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One line of an Instagram in-DM order cart (Wave 7). NOT a JPA entity: instances live only inside
 * {@link InstagramSubscriber#getOrderCart()}, which persists the whole {@code List<InstagramCartLine>}
 * as a single JSONB document via {@code @JdbcTypeCode(SqlTypes.JSON)} — so this is a plain,
 * Jackson-round-trippable POJO (no-arg constructor + getters/setters via Lombok {@code @Data}).
 *
 * <p>Every field is denormalised from the {@code Product} at the moment it was added to the cart:
 * {@code productId} still ties the eventual {@link com.elcafe.modules.order.entity.OrderItem} back to
 * the catalogue, but {@code productName} and {@code unitPrice} are captured here so an in-flight cart
 * renders and totals correctly even if the product is renamed, repriced, or unpublished between the DM
 * turn that added it and checkout — the same "snapshot the line" stance {@code OrderItem} itself takes.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} so a cart written by an older shape of this
 * class (an extra field added later, then rolled back) deserialises rather than blowing up a live
 * conversation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstagramCartLine {

    /** Catalogue product this line orders. */
    private Long productId;

    /** Product name captured at add-to-cart time (see class javadoc — survives a later rename). */
    private String productName;

    /** Unit price captured at add-to-cart time (survives a later reprice). */
    private BigDecimal unitPrice;

    /** How many of this product. Always >= 1 once the quantity step has finalised the line. */
    private Integer quantity;

    /** Line total = unitPrice × quantity, null-safe. */
    public BigDecimal lineTotal() {
        if (unitPrice == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}

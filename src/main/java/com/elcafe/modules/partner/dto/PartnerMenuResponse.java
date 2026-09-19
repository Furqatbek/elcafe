package com.elcafe.modules.partner.dto;

import com.elcafe.modules.menu.enums.ItemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The menu as an integrating partner needs to see it, which is not the same shape the storefront needs.
 *
 * <p>Three deliberate differences from {@code PublicMenuCategoryDTO}:
 *
 * <ul>
 *   <li><b>Variants are included.</b> The public payload advertises {@code hasVariants: true} and then
 *       omits them, which is survivable for our own UI (it fetches them on demand) and useless to a
 *       partner building a static catalogue — they would list a two-size drink at one price.</li>
 *   <li><b>Add-on groups are included.</b> An order may carry add-ons, so a partner that cannot see
 *       them cannot construct half the orders their customers want.</li>
 *   <li><b>Sold-out items are present with {@code available: false} rather than absent.</b> A partner
 *       diffs this payload against their catalogue; dropping an 86'd item makes "sold out until
 *       tonight" indistinguishable from "delisted", so they delete the item and lose their own
 *       mapping, then recreate it tomorrow with a new id.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerMenuResponse {

    private Long restaurantId;

    private String restaurantName;

    /** Whether the venue is taking orders at all right now. A partner should stop offering it if false. */
    private Boolean acceptingOrders;

    private BigDecimal deliveryFee;

    private String currency;

    /**
     * The most recent {@code updatedAt} anywhere in this payload. A partner can store it and skip
     * rebuilding its catalogue when it has not moved — cheaper for both sides than diffing the tree.
     */
    private LocalDateTime menuVersion;

    @Builder.Default
    private List<Category> categories = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Category {
        private Long id;
        private String name;
        private String description;
        private String imageUrl;
        private Integer sortOrder;
        @Builder.Default
        private List<Product> products = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Product {
        private Long id;
        private String name;
        private String description;
        private String imageUrl;
        /** Base price. When {@code variants} is non-empty the variant's own price applies instead. */
        private BigDecimal price;
        /**
         * The same number as {@link #price}, repeated under the name importers look for.
         *
         * <p>Not redundancy for its own sake. An importer that treats {@code price} as a cost and
         * adds its own margin, but takes {@code priceWithMargin} verbatim, will charge the customer
         * more than we published if only the first is sent — and the order it then pushes back
         * disagrees with our total, so we refuse it and neither side can see why. Both keys carry the
         * channel price, which already has this venue's markup in it, so there is nothing left to add.
         */
        private BigDecimal priceWithMargin;
        private ItemType itemType;
        private Integer sortOrder;
        /** False for an item that exists but cannot be ordered right now. Never a reason to omit it. */
        private Boolean available;
        private Boolean soldByWeight;
        private String weightUnit;
        private BigDecimal minWeight;
        private BigDecimal maxWeight;
        @Builder.Default
        private List<Variant> variants = new ArrayList<>();
        @Builder.Default
        private List<AddOnGroup> addOnGroups = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Variant {
        private Long id;
        private String name;
        private String description;
        private BigDecimal price;
        /** Same number as {@link #price} — see {@link Product#priceWithMargin}. */
        private BigDecimal priceWithMargin;
        private String sku;
        private Integer sortOrder;
        private Boolean available;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddOnGroup {
        private Long id;
        private String name;
        private String description;
        private Boolean required;
        private Integer minSelection;
        private Integer maxSelection;
        @Builder.Default
        private List<AddOn> addOns = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddOn {
        private Long id;
        private String name;
        private String description;
        private BigDecimal price;
        private Integer sortOrder;
        private Boolean available;
    }
}

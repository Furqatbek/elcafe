package com.elcafe.modules.partner.dto;

import com.elcafe.modules.partner.enums.PriceAdjustmentType;
import com.elcafe.modules.partner.enums.PriceRuleScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A partner as the admin UI sees it. Note what is absent: the API key. Only {@link #apiKeyPrefix} is
 * returned, because the key is hashed at rest and genuinely cannot be recovered — if an operator has
 * lost it, the answer is to rotate, not to look it up.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerAdminResponse {

    private Long id;
    private String name;
    private String slug;
    /** Leading characters of the key, so an operator can match this record to a key they hold. */
    private String apiKeyPrefix;
    private String contactEmail;
    private Boolean active;
    private OffsetDateTime createdAt;

    @Builder.Default
    private List<Grant> restaurants = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Grant {
        private Long restaurantId;
        private String restaurantName;
        private Boolean canReadMenu;
        private Boolean canPushOrders;
        private Boolean active;
        /** Default channel markup at this venue: NONE, PERCENT or AMOUNT. */
        private PriceAdjustmentType priceAdjustmentType;
        private BigDecimal priceAdjustmentValue;
        private BigDecimal priceRounding;
        /** Per-item exceptions to the default above. */
        @Builder.Default
        private List<PriceRule> priceRules = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceRule {
        private Long id;
        private PriceRuleScope scope;
        private Long targetId;
        /** Resolved name of the category/product/variant, so the UI need not look it up. */
        private String targetName;
        private PriceAdjustmentType adjustmentType;
        private BigDecimal adjustmentValue;
        private Boolean active;
    }
}

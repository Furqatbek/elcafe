package com.elcafe.modules.partner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    }
}

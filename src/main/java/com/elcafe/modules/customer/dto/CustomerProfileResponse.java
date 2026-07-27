package com.elcafe.modules.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Everything known about one guest, gathered into the single view the profile page renders (V183).
 *
 * <p>Assembled rather than stored: the identity lives on {@code customers}, the spend on {@code orders},
 * the balance in the loyalty tables, and the preferences in {@code customer_preference}. Nothing here is
 * a second copy, so nothing here can go stale.
 *
 * <p>Conversation history is deliberately NOT part of this payload — it is paged separately, because
 * dumping a guest's entire message history to render a header is the one thing guaranteed to make this
 * page slow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProfileResponse {

    // ---- identity -----------------------------------------------------------------------------
    private Long id;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private LocalDate birthDate;
    private String language;
    private String notes;
    private String tags;
    private String registrationSource;
    private OffsetDateTime createdAt;

    /** Set when an employee registered this guest at the till rather than the guest registering online. */
    private Long registeredByUserId;

    // ---- what they like -----------------------------------------------------------------------
    private List<CustomerPreferenceResponse> preferences;

    // ---- what they have spent -----------------------------------------------------------------
    private PurchaseStats purchases;

    // ---- where they stand on loyalty ----------------------------------------------------------
    private LoyaltySummary loyalty;

    /**
     * Lifetime purchase behaviour, computed from the guest's orders. Counts only orders that actually
     * happened — a cancelled order is not a purchase and must not inflate spend or the average.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseStats {
        private Long orderCount;
        private BigDecimal lifetimeSpend;
        private BigDecimal averageOrderValue;
        private OffsetDateTime firstOrderAt;
        private OffsetDateTime lastOrderAt;
        /** Most-ordered items, commonest first — the closest thing to an observed "like". */
        private List<TopItem> topItems;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopItem {
        private String productName;
        private Long timesOrdered;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoyaltySummary {
        private BigDecimal currentBalance;
        private BigDecimal totalEarned;
        private String tierName;
        /** Null when the guest has no loyalty record yet — they have simply never earned anything. */
        private OffsetDateTime memberSince;
    }
}

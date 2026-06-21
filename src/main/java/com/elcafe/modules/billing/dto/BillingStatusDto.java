package com.elcafe.modules.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A restaurant's current plan state (GET /api/v1/billing/me). Drives the frontend plan awareness:
 * which modules are unlocked ({@code featureCodes}), the expiry banner ({@code daysUntilExpiry},
 * {@code inGracePeriod}), and read-only mode ({@code readOnly}). {@code planExpiresAt == null} means
 * no expiry (free Start tier).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingStatusDto {
    private Long restaurantId;
    private String planCode;
    private String planName;
    private List<String> featureCodes;
    private LocalDateTime planExpiresAt;
    private Boolean isTrial;
    /** Days from today until expiry; negative once past expiry, null when there is no expiry. */
    private Long daysUntilExpiry;
    /** Past expiry but within the 3-day grace window — full access, prominent reminder. */
    private Boolean inGracePeriod;
    /** Past expiry + grace — write actions are blocked until the plan is renewed. */
    private Boolean readOnly;
}

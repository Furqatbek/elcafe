package com.elcafe.modules.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One row of the SUPER_ADMIN platform tenant list (GET /api/v1/platform/tenants): a restaurant plus
 * its current subscription state. The billing fields mirror {@link BillingStatusDto}; {@code name} and
 * {@code active} come from the restaurant itself ({@code active == false} means the operator has
 * suspended the tenant).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSummaryDto {
    private Long restaurantId;
    private String name;
    private boolean active;
    private String planCode;
    private String planName;
    private Boolean isTrial;
    private LocalDateTime planExpiresAt;
    private Long daysUntilExpiry;
    private Boolean inGracePeriod;
    private Boolean readOnly;
}

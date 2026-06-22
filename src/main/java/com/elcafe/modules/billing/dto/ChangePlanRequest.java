package com.elcafe.modules.billing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SUPER_ADMIN request to point a tenant at a plan (POST /api/v1/platform/tenants/{id}/plan). The
 * restaurant id travels in the path; this is the body. Mirrors {@link SetPlanRequest} minus the id.
 */
@Data
public class ChangePlanRequest {

    /** Target plan code: start, advance, pro. */
    @NotBlank
    private String planCode;

    /** When the new plan should expire. Null = no expiry (e.g. the free Start tier). */
    private LocalDateTime planExpiresAt;

    /** Mark this as a trial assignment (drives "Trial ends in N days" banner copy). */
    private Boolean isTrial;
}

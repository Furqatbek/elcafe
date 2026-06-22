package com.elcafe.modules.billing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** SUPER_ADMIN request to push a tenant's expiry out by N days (POST /api/v1/platform/tenants/{id}/extend). */
@Data
public class ExtendPlanRequest {

    /** Days to add. Capped at ten years so a typo can't set an absurd expiry. */
    @Positive
    @Max(3650)
    private int days;
}

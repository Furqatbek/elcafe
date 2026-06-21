package com.elcafe.modules.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/** Admin request to change a restaurant's plan (POST /api/v1/billing/admin/set-plan). */
@Data
public class SetPlanRequest {

    @NotNull
    private Long restaurantId;

    /** Target plan code: start, advance, pro. */
    @NotBlank
    private String planCode;

    /** When the new plan should expire. Null = no expiry (e.g. the free Start tier). */
    private LocalDateTime planExpiresAt;

    /** Mark this as a trial assignment (drives "Trial ends in N days" banner copy). */
    private Boolean isTrial;
}

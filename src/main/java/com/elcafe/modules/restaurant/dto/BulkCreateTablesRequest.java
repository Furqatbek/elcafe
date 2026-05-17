package com.elcafe.modules.restaurant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bulk-create N tables in one call. Numbers are derived as
 * {@code prefix + startNumber, prefix + startNumber+1, …}.
 * Capacity/section/notes apply to every table in the batch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCreateTablesRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    /** Optional. e.g. "T" or "VIP-". Empty means just numbers. */
    private String prefix;

    /** First number in the sequence. Defaults to 1. */
    @Min(value = 0, message = "Start number cannot be negative")
    private Integer startNumber;

    @NotNull(message = "Count is required")
    @Min(value = 1, message = "Count must be at least 1")
    @Max(value = 200, message = "Cannot create more than 200 tables at once")
    private Integer count;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    private String section;

    private Boolean active;

    private String notes;
}

package com.elcafe.modules.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** One available tier in the catalogue (GET /api/v1/billing/plans). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanSummaryDto {
    private String code;
    private String name;
    /** Monthly price in UZS so'm (no fractional unit). */
    private Long monthlyPrice;
    private List<String> featureCodes;
    private Integer sortOrder;
}

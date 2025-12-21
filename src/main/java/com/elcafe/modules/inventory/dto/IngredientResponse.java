package com.elcafe.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private String name;
    private String description;
    private String unit;
    private BigDecimal currentStock;
    private BigDecimal minimumStock;
    private BigDecimal reorderLevel;
    private BigDecimal reorderQuantity;
    private BigDecimal costPerUnit;
    private String supplier;
    private Long supplierId;
    private String supplierName;
    private String sku;
    private Boolean active;
    private Boolean trackInventory;

    // Expiry tracking fields
    private Boolean trackExpiry;
    private Integer defaultShelfLifeDays;
    private Integer expiryAlertDays;

    // Batch summary (when tracking expiry)
    private Long activeBatchCount;
    private Long expiringBatchCount;
    private Long expiredBatchCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

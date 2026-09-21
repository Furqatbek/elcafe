package com.elcafe.modules.inventory.dto;

import com.elcafe.modules.inventory.entity.InventoryBatch;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchResponse {

    private Long id;
    private Long ingredientId;
    private String ingredientName;
    private String batchNumber;
    private BigDecimal quantity;
    private BigDecimal initialQuantity;
    private LocalDate receivedDate;
    private LocalDate expiryDate;
    private BigDecimal costPerUnit;
    private Long supplierId;
    private String supplierName;
    private String poReference;
    private InventoryBatch.Status status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Computed fields
    private Long daysUntilExpiry;
    private Boolean isExpired;
    private Boolean isExpiringSoon;
    private String expiryStatus; // OK, EXPIRING_SOON, EXPIRED

    public static BatchResponse fromEntity(InventoryBatch batch, int expiryAlertDays) {
        BatchResponse response = BatchResponse.builder()
                .id(batch.getId())
                .ingredientId(batch.getIngredient().getId())
                .ingredientName(batch.getIngredient().getName())
                .batchNumber(batch.getBatchNumber())
                .quantity(batch.getQuantity())
                .initialQuantity(batch.getInitialQuantity())
                .receivedDate(batch.getReceivedDate())
                .expiryDate(batch.getExpiryDate())
                .costPerUnit(batch.getCostPerUnit())
                .supplierId(batch.getSupplier() != null ? batch.getSupplier().getId() : null)
                .supplierName(batch.getSupplier() != null ? batch.getSupplier().getName() : null)
                .poReference(batch.getPoReference())
                .status(batch.getStatus())
                .notes(batch.getNotes())
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .build();

        // Calculate expiry-related fields
        if (batch.getExpiryDate() != null) {
            response.setDaysUntilExpiry(batch.getDaysUntilExpiry());
            response.setIsExpired(batch.isExpired());
            response.setIsExpiringSoon(batch.isExpiringSoon(expiryAlertDays));

            if (batch.isExpired()) {
                response.setExpiryStatus("EXPIRED");
            } else if (batch.isExpiringSoon(expiryAlertDays)) {
                response.setExpiryStatus("EXPIRING_SOON");
            } else {
                response.setExpiryStatus("OK");
            }
        } else {
            response.setDaysUntilExpiry(null);
            response.setIsExpired(false);
            response.setIsExpiringSoon(false);
            response.setExpiryStatus("NO_EXPIRY");
        }

        return response;
    }
}

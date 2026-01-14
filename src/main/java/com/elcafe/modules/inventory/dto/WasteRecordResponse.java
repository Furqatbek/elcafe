package com.elcafe.modules.inventory.dto;

import com.elcafe.modules.inventory.entity.WasteRecord;
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
public class WasteRecordResponse {

    private Long id;
    private Long restaurantId;
    private Long ingredientId;
    private String ingredientName;
    private String ingredientUnit;
    private Long batchId;
    private String batchNumber;
    private LocalDate wasteDate;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private String wasteReason;
    private String wasteReasonLabel;
    private String recordedBy;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WasteRecordResponse fromEntity(WasteRecord entity) {
        return WasteRecordResponse.builder()
                .id(entity.getId())
                .restaurantId(entity.getRestaurant().getId())
                .ingredientId(entity.getIngredient().getId())
                .ingredientName(entity.getIngredient().getName())
                .ingredientUnit(entity.getIngredient().getUnit())
                .batchId(entity.getBatch() != null ? entity.getBatch().getId() : null)
                .batchNumber(entity.getBatch() != null ? entity.getBatch().getBatchNumber() : null)
                .wasteDate(entity.getWasteDate())
                .quantity(entity.getQuantity())
                .unitCost(entity.getUnitCost())
                .totalCost(entity.getTotalCost())
                .wasteReason(entity.getWasteReason().name())
                .wasteReasonLabel(entity.getWasteReason().getLabel())
                .recordedBy(entity.getRecordedBy())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

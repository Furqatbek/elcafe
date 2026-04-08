package com.elcafe.modules.inventory.dto;

import com.elcafe.modules.inventory.entity.ProductionBatch;
import com.elcafe.modules.inventory.entity.ProductionBatchInput;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionBatchResponse {

    private Long id;
    private Long restaurantId;
    private Long productId;
    private String productName;
    private String batchNumber;
    private String name;
    private BigDecimal outputQuantity;
    private String outputUnit;
    private BigDecimal remainingQuantity;
    private BigDecimal totalInputCost;
    private BigDecimal costPerUnit;
    private ProductionBatch.Status status;
    private String preparedBy;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;
    private String notes;
    private LocalDateTime createdAt;
    private List<InputDetail> inputs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InputDetail {
        private Long id;
        private Long ingredientId;
        private String ingredientName;
        private BigDecimal plannedQuantity;
        private BigDecimal actualQuantity;
        private String unit;
        private BigDecimal costPerUnit;
        private BigDecimal totalCost;
        private String notes;
    }

    public static ProductionBatchResponse fromEntity(ProductionBatch batch) {
        ProductionBatchResponseBuilder builder = ProductionBatchResponse.builder()
                .id(batch.getId())
                .restaurantId(batch.getRestaurant().getId())
                .batchNumber(batch.getBatchNumber())
                .name(batch.getName())
                .outputQuantity(batch.getOutputQuantity())
                .outputUnit(batch.getOutputUnit())
                .remainingQuantity(batch.getRemainingQuantity())
                .totalInputCost(batch.getTotalInputCost())
                .costPerUnit(batch.getCostPerUnit())
                .status(batch.getStatus())
                .preparedBy(batch.getPreparedBy())
                .startedAt(batch.getStartedAt())
                .completedAt(batch.getCompletedAt())
                .expiresAt(batch.getExpiresAt())
                .notes(batch.getNotes())
                .createdAt(batch.getCreatedAt());

        if (batch.getProduct() != null) {
            builder.productId(batch.getProduct().getId())
                    .productName(batch.getProduct().getName());
        }

        if (batch.getInputs() != null) {
            builder.inputs(batch.getInputs().stream()
                    .map(ProductionBatchResponse::toInputDetail)
                    .toList());
        }

        return builder.build();
    }

    private static InputDetail toInputDetail(ProductionBatchInput input) {
        InputDetail.InputDetailBuilder builder = InputDetail.builder()
                .id(input.getId())
                .plannedQuantity(input.getPlannedQuantity())
                .actualQuantity(input.getActualQuantity())
                .unit(input.getUnit())
                .costPerUnit(input.getCostPerUnit())
                .totalCost(input.getTotalCost())
                .notes(input.getNotes());

        if (input.getIngredient() != null) {
            builder.ingredientId(input.getIngredient().getId())
                    .ingredientName(input.getIngredient().getName());
        }

        return builder.build();
    }
}

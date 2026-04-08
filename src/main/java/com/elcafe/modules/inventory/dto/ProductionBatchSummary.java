package com.elcafe.modules.inventory.dto;

import com.elcafe.modules.inventory.entity.ProductionBatch;
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
public class ProductionBatchSummary {

    private Long id;
    private String name;
    private String productName;
    private BigDecimal outputQuantity;
    private String outputUnit;
    private BigDecimal remainingQuantity;
    private BigDecimal costPerUnit;
    private ProductionBatch.Status status;
    private String preparedBy;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    public static ProductionBatchSummary fromEntity(ProductionBatch batch) {
        ProductionBatchSummaryBuilder builder = ProductionBatchSummary.builder()
                .id(batch.getId())
                .name(batch.getName())
                .outputQuantity(batch.getOutputQuantity())
                .outputUnit(batch.getOutputUnit())
                .remainingQuantity(batch.getRemainingQuantity())
                .costPerUnit(batch.getCostPerUnit())
                .status(batch.getStatus())
                .preparedBy(batch.getPreparedBy())
                .expiresAt(batch.getExpiresAt())
                .createdAt(batch.getCreatedAt());

        if (batch.getProduct() != null) {
            builder.productName(batch.getProduct().getName());
        }

        return builder.build();
    }
}

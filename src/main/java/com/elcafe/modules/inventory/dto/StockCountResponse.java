package com.elcafe.modules.inventory.dto;

import com.elcafe.modules.inventory.entity.StockCount;
import com.elcafe.modules.inventory.entity.StockCountItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCountResponse {

    private Long id;
    private Long restaurantId;
    private String countNumber;
    private String countType;
    private String status;
    private LocalDate scheduledDate;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime approvedAt;
    private String initiatedBy;
    private String countedBy;
    private String reviewedBy;
    private String approvedBy;
    private String notes;
    private Integer totalItems;
    private Integer countedItems;
    private Integer varianceCount;
    private BigDecimal totalVarianceValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<StockCountItemResponse> items;

    public static StockCountResponse fromEntity(StockCount entity) {
        return StockCountResponse.builder()
                .id(entity.getId())
                .restaurantId(entity.getRestaurant().getId())
                .countNumber(entity.getCountNumber())
                .countType(entity.getCountType().name())
                .status(entity.getStatus().name())
                .scheduledDate(entity.getScheduledDate())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .approvedAt(entity.getApprovedAt())
                .initiatedBy(entity.getInitiatedBy())
                .countedBy(entity.getCountedBy())
                .reviewedBy(entity.getReviewedBy())
                .approvedBy(entity.getApprovedBy())
                .notes(entity.getNotes())
                .totalItems(entity.getTotalItems())
                .countedItems(entity.getCountedItems())
                .varianceCount(entity.getVarianceCount())
                .totalVarianceValue(entity.getTotalVarianceValue())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .items(entity.getItems() != null
                        ? entity.getItems().stream()
                                .map(StockCountItemResponse::fromEntity)
                                .collect(Collectors.toList())
                        : null)
                .build();
    }

    public static StockCountResponse fromEntityWithoutItems(StockCount entity) {
        return StockCountResponse.builder()
                .id(entity.getId())
                .restaurantId(entity.getRestaurant().getId())
                .countNumber(entity.getCountNumber())
                .countType(entity.getCountType().name())
                .status(entity.getStatus().name())
                .scheduledDate(entity.getScheduledDate())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .approvedAt(entity.getApprovedAt())
                .initiatedBy(entity.getInitiatedBy())
                .countedBy(entity.getCountedBy())
                .reviewedBy(entity.getReviewedBy())
                .approvedBy(entity.getApprovedBy())
                .notes(entity.getNotes())
                .totalItems(entity.getTotalItems())
                .countedItems(entity.getCountedItems())
                .varianceCount(entity.getVarianceCount())
                .totalVarianceValue(entity.getTotalVarianceValue())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockCountItemResponse {
        private Long id;
        private Long ingredientId;
        private String ingredientName;
        private String unit;
        private BigDecimal systemQuantity;
        private BigDecimal countedQuantity;
        private BigDecimal varianceQuantity;
        private BigDecimal variancePercentage;
        private BigDecimal varianceValue;
        private String varianceReason;
        private String status;
        private String countedBy;
        private LocalDateTime countedAt;
        private String notes;

        public static StockCountItemResponse fromEntity(StockCountItem entity) {
            return StockCountItemResponse.builder()
                    .id(entity.getId())
                    .ingredientId(entity.getIngredient().getId())
                    .ingredientName(entity.getIngredient().getName())
                    .unit(entity.getIngredient().getUnit())
                    .systemQuantity(entity.getSystemQuantity())
                    .countedQuantity(entity.getCountedQuantity())
                    .varianceQuantity(entity.getVarianceQuantity())
                    .variancePercentage(entity.getVariancePercentage())
                    .varianceValue(entity.getVarianceValue())
                    .varianceReason(entity.getVarianceReason() != null ? entity.getVarianceReason().name() : null)
                    .status(entity.getStatus().name())
                    .countedBy(entity.getCountedBy())
                    .countedAt(entity.getCountedAt())
                    .notes(entity.getNotes())
                    .build();
        }
    }
}

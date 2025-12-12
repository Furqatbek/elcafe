package com.elcafe.modules.financial.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivePurchaseOrderRequest {

    @NotNull(message = "Actual delivery date is required")
    private LocalDate actualDeliveryDate;

    @NotNull(message = "Received by is required")
    private String receivedBy;

    @NotEmpty(message = "At least one item must be received")
    @Valid
    private List<ReceivedItemRequest> items;

    private String notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceivedItemRequest {

        @NotNull(message = "Item ID is required")
        private Long itemId;

        @NotNull(message = "Received quantity is required")
        private BigDecimal receivedQuantity;

        private String notes;
    }
}

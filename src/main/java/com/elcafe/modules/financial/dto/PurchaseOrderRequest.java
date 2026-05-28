package com.elcafe.modules.financial.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
public class PurchaseOrderRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    private Long supplierId;

    private String supplierName;

    private String supplierContact;

    private String supplierAddress;

    @NotNull(message = "Order date is required")
    private LocalDate orderDate;

    private LocalDate expectedDeliveryDate;

    private BigDecimal taxAmount;

    private BigDecimal shippingCost;

    private String notes;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<PurchaseOrderItemRequest> items;

    /**
     * When true, the server runs approve + receive (all items at their
     * ordered quantity) + record full payment in a single transaction so
     * the PO comes out fully RECEIVED and PAID. Lets clients skip the
     * multi-step button flow for trivial "just-bought-this" purchases.
     */
    private Boolean autoFinalize;

    /** Optional override; defaults to CASH when autoFinalize is on. */
    private String paymentMethod;

    /** Optional override; defaults to today when autoFinalize is on. */
    private LocalDate paymentDate;
}

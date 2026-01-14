package com.elcafe.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POSuggestionResponse {

    private Long supplierId;
    private String supplierName;
    private String supplierCode;
    private String supplierContact;
    private String supplierAddress;
    private String supplierPaymentTerms;
    private List<POSuggestionItemResponse> items;
    private BigDecimal estimatedTotal;
    private Urgency urgency;
    private int itemCount;

    public enum Urgency {
        CRITICAL,  // currentStock <= 0 or items out of stock
        HIGH,      // currentStock <= minimumStock
        MEDIUM     // currentStock <= reorderLevel
    }
}

package com.elcafe.modules.inventory.dto;

import com.elcafe.modules.inventory.entity.WasteRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WasteRecordRequest {

    private Long restaurantId;
    private Long ingredientId;
    private Long batchId;  // Optional - link to specific batch
    private LocalDate wasteDate;
    private BigDecimal quantity;
    private BigDecimal unitCost;  // Optional - defaults to ingredient cost
    private WasteRecord.WasteReason wasteReason;
    private String recordedBy;
    private String notes;
}

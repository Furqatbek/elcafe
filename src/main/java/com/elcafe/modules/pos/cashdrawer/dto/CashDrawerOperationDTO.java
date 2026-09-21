package com.elcafe.modules.pos.cashdrawer.dto;

import com.elcafe.modules.pos.cashdrawer.enums.CashOperationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDrawerOperationDTO {

    private Long id;
    private CashOperationType operationType;
    private BigDecimal amount;
    private String reason;
    private String operatorName;
    private Long orderId;
    private OffsetDateTime createdAt;
}

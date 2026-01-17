package com.elcafe.modules.selfservice.dto;

import com.elcafe.modules.selfservice.enums.SelfServiceOrderType;
import lombok.Data;

@Data
public class SubmitOrderRequest {
    private String customerName;
    private String customerPhone;
    private SelfServiceOrderType orderType = SelfServiceOrderType.DINE_IN;
    private String specialInstructions;
    private String notes;
    private String couponCode;
}

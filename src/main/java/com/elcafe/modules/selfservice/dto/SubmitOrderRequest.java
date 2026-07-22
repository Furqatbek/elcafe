package com.elcafe.modules.selfservice.dto;

import com.elcafe.modules.selfservice.enums.SelfServiceOrderType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitOrderRequest {

    @Size(max = 100, message = "Customer name cannot exceed 100 characters")
    private String customerName;

    @Size(max = 20, message = "Customer phone cannot exceed 20 characters")
    @Pattern(regexp = "^\\+?[\\d\\s\\-()]*$", message = "Invalid phone number format")
    private String customerPhone;

    @NotNull(message = "Order type is required")
    private SelfServiceOrderType orderType = SelfServiceOrderType.DINE_IN;

    @Size(max = 500, message = "Special instructions cannot exceed 500 characters")
    private String specialInstructions;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    @Size(max = 50, message = "Coupon code cannot exceed 50 characters")
    private String couponCode;
}

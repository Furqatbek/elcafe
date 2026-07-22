package com.elcafe.modules.order.dto.payment;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntentRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    private String paymentMethodId;

    private String returnUrl;
}

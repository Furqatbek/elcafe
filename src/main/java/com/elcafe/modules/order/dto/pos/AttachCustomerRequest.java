package com.elcafe.modules.order.dto.pos;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Attach an existing customer to a POS order. Exactly one of
 * customerId, qrCode, or phone must be supplied — the service
 * resolves it to a Customer and links it to the order so the
 * loyalty wallet credit fires on order completion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachCustomerRequest {

    private Long customerId;
    private String qrCode;
    private String phone;

    @AssertTrue(message = "Exactly one of customerId, qrCode, or phone must be provided")
    public boolean isExactlyOneIdentifier() {
        int count = 0;
        if (customerId != null) count++;
        if (qrCode != null && !qrCode.isBlank()) count++;
        if (phone != null && !phone.isBlank()) count++;
        return count == 1;
    }
}

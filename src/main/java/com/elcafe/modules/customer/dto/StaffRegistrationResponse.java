package com.elcafe.modules.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * V182: the outcome of a till-side registration, shaped for what the cashier has to say out loud next.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffRegistrationResponse {

    private Long customerId;

    private String customerName;

    /**
     * True when that phone number already belonged to a customer, so this call linked the order to the
     * existing person instead of creating a second record. The POS says so plainly rather than silently
     * minting a duplicate — and it explains a zero bonus without the cashier thinking something broke.
     */
    private Boolean alreadyRegistered;

    /**
     * What was actually credited — zero when the bonus is switched off, unconfigured, or this guest has
     * had it before. The cashier should only promise what this says.
     */
    private BigDecimal bonusGranted;

    /** True when the order named in the request was attached to this customer. */
    private Boolean orderLinked;
}

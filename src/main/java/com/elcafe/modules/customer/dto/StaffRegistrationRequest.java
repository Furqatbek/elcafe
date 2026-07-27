package com.elcafe.modules.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * V182: a guest registered at the till by an employee, typically while taking payment.
 *
 * <p>Deliberately tiny. This is keyed in at a counter with people waiting, so it asks for the least
 * that still produces a usable customer record — a name to greet them by and the phone that identifies
 * them. There is no OTP field: the staff-assisted door does not verify the number, because making a
 * guest read back six digits at a busy till is how the offer stops being made. See {@code
 * StaffAssistedRegistrationService} for what that trades away and how it stays reviewable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffRegistrationRequest {

    /**
     * The order being paid when the offer was made. Optional — a guest can be registered without one —
     * but when present the new customer is linked to it, so this visit counts as their first.
     */
    private Long orderId;

    @NotBlank(message = "Customer name is required")
    @Size(max = 100, message = "Name must be less than 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must be less than 100 characters")
    private String lastName;

    @NotBlank(message = "Phone number is required")
    @Size(max = 20, message = "Phone number must be less than 20 characters")
    private String phone;
}

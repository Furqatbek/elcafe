package com.elcafe.modules.waiter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiterAuthRequest {

    // V151: PIN codes are unique per restaurant, so login must name the restaurant being signed
    // into. The POS device is configured per venue and supplies it.
    @NotNull(message = "Restaurant is required")
    private Long restaurantId;

    @NotBlank(message = "PIN code is required")
    @Pattern(regexp = "^[0-9]{4,6}$", message = "PIN code must be 4-6 digits")
    private String pinCode;
}

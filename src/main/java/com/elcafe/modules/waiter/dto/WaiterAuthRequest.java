package com.elcafe.modules.waiter.dto;

import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "PIN code is required")
    @Pattern(regexp = "^[0-9]{4,6}$", message = "PIN code must be 4-6 digits")
    private String pinCode;
}

package com.elcafe.modules.customer.dto;

import com.elcafe.modules.customer.entity.CustomerPreference;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPreferenceRequest {

    @NotNull(message = "Preference type is required")
    private CustomerPreference.Type preferenceType;

    @NotBlank(message = "Value is required")
    @Size(max = 120, message = "Value must be less than 120 characters")
    private String value;

    @Size(max = 500, message = "Note must be less than 500 characters")
    private String note;

    // No `source` field: anything created through this request was typed by a person, so it is always
    // MANUAL. Letting a caller claim DERIVED would let a guess be recorded as something the guest said.
}

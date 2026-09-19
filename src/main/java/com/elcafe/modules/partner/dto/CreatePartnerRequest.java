package com.elcafe.modules.partner.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePartnerRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    /**
     * Lowercase handle, immutable once created. It namespaces the partner's orders and payment gateway
     * records, so the character set is restricted to things that survive a URL, a log line and a
     * reconciliation spreadsheet unchanged.
     */
    @NotBlank(message = "Slug is required")
    @Size(max = 100, message = "Slug must not exceed 100 characters")
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*$",
            message = "Slug must be lowercase letters, digits, hyphens or underscores")
    private String slug;

    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Contact email must not exceed 255 characters")
    private String contactEmail;

    /**
     * What this partner adds at their own checkout, as a percentage of our published price. Optional;
     * omitted means none, or not yet known. Display only — see {@code Partner#customerFeePercent}.
     */
    @jakarta.validation.constraints.DecimalMin(value = "0", message = "A partner fee cannot be negative")
    @jakarta.validation.constraints.DecimalMax(value = "100", message = "A partner fee cannot exceed 100%")
    private java.math.BigDecimal customerFeePercent;
}

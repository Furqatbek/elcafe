package com.elcafe.modules.customer.dto;

import com.elcafe.modules.customer.entity.CustomerPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPreferenceResponse {

    private Long id;
    private String preferenceType;
    private String value;
    private String note;
    /** MANUAL when a person recorded it, DERIVED when it was inferred — never blur the two in the UI. */
    private String source;
    private OffsetDateTime createdAt;

    public static CustomerPreferenceResponse from(CustomerPreference preference) {
        return CustomerPreferenceResponse.builder()
                .id(preference.getId())
                .preferenceType(preference.getPreferenceType() != null
                        ? preference.getPreferenceType().name() : null)
                .value(preference.getValue())
                .note(preference.getNote())
                .source(preference.getSource() != null ? preference.getSource().name() : null)
                .createdAt(preference.getCreatedAt())
                .build();
    }
}

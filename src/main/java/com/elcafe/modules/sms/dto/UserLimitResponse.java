package com.elcafe.modules.sms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for user limit information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLimitResponse {

    private String message;

    @JsonProperty("data")
    private LimitData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LimitData {
        @JsonProperty("sms_count")
        private Long smsCount;

        @JsonProperty("limit")
        private Long limit;
    }
}

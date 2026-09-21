package com.elcafe.modules.sms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for dispatch status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchStatusResponse {

    private String message;

    @JsonProperty("data")
    private DispatchData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatchData {
        private Long id;

        private String name;

        private String status;

        @JsonProperty("total_count")
        private Integer totalCount;

        @JsonProperty("sent_count")
        private Integer sentCount;

        @JsonProperty("delivered_count")
        private Integer deliveredCount;

        @JsonProperty("failed_count")
        private Integer failedCount;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("updated_at")
        private String updatedAt;
    }
}

package com.elcafe.modules.sms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for user messages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMessagesResponse {

    private String message;

    @JsonProperty("data")
    private MessagesData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessagesData {
        @JsonProperty("current_page")
        private Integer currentPage;

        @JsonProperty("data")
        private List<MessageItem> messages;

        @JsonProperty("first_page_url")
        private String firstPageUrl;

        @JsonProperty("from")
        private Integer from;

        @JsonProperty("last_page")
        private Integer lastPage;

        @JsonProperty("last_page_url")
        private String lastPageUrl;

        @JsonProperty("next_page_url")
        private String nextPageUrl;

        @JsonProperty("path")
        private String path;

        @JsonProperty("per_page")
        private Integer perPage;

        @JsonProperty("prev_page_url")
        private String prevPageUrl;

        @JsonProperty("to")
        private Integer to;

        @JsonProperty("total")
        private Integer total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageItem {
        private Long id;

        @JsonProperty("user_id")
        private Long userId;

        @JsonProperty("mobile_phone")
        private String mobilePhone;

        private String message;

        private String status;

        @JsonProperty("status_name")
        private String statusName;

        @JsonProperty("dispatch_id")
        private Long dispatchId;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("updated_at")
        private String updatedAt;
    }
}

package com.elcafe.modules.push.dto;

import com.elcafe.modules.push.enums.PushNotificationType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for sending push notifications.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushNotificationRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String body;
    private String icon;
    private String image;
    private String badge;
    private String tag;
    private Map<String, Object> data;
    private List<PushAction> actions;
    private PushNotificationType type;

    /**
     * Action button for push notification.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PushAction {
        private String action;
        private String title;
        private String icon;
    }
}

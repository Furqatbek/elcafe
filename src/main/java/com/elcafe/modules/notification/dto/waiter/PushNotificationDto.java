package com.elcafe.modules.notification.dto.waiter;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * The waiter app's PushNotification shape. {@code id} is the notificationId the
 * app uses everywhere (mark-read, push data payload).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PushNotificationDto {
    private Long id;
    private String type;              // one of the app's NotificationType values
    private String title;
    private String body;
    private Map<String, Object> data; // parsed from Notification.metadata
    private boolean read;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}

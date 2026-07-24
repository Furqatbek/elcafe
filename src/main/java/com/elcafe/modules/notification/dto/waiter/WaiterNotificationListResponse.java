package com.elcafe.modules.notification.dto.waiter;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WaiterNotificationListResponse {
    private List<PushNotificationDto> notifications;
    private long unreadCount;
    private long totalCount;
}

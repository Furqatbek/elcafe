package com.elcafe.modules.notification.dto.waiter;

import lombok.Builder;
import lombok.Data;

/**
 * Notification preferences. Used both as the GET response (all fields present)
 * and the PUT request (every field optional — nulls are left unchanged), hence
 * boxed types throughout.
 */
@Data
@Builder
public class WaiterNotificationPreferenceDto {
    private Boolean orderUpdates;
    private Boolean tableReady;
    private Boolean kitchenAlerts;
    private Boolean newOrders;
    private Boolean paymentNotifications;
    private Boolean systemAlerts;
    private Boolean shiftReminders;
    private Boolean soundEnabled;
    private Boolean vibrationEnabled;
    private Boolean quietHoursEnabled;
    private String quietHoursStart; // "HH:mm"
    private String quietHoursEnd;   // "HH:mm"
}

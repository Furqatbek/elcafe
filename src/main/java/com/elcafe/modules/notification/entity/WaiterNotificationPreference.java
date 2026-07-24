package com.elcafe.modules.notification.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-waiter notification preferences (category toggles + sound/vibration +
 * quiet hours). One row per waiter.
 */
@Entity
@Table(name = "waiter_notification_preferences", indexes = {
        @Index(name = "idx_waiter_pref_waiter", columnList = "waiter_id", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiterNotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "waiter_id", nullable = false, unique = true)
    private Long waiterId;

    @Builder.Default @Column(name = "order_updates", nullable = false) private boolean orderUpdates = true;
    @Builder.Default @Column(name = "table_ready", nullable = false) private boolean tableReady = true;
    @Builder.Default @Column(name = "kitchen_alerts", nullable = false) private boolean kitchenAlerts = true;
    @Builder.Default @Column(name = "new_orders", nullable = false) private boolean newOrders = true;
    @Builder.Default @Column(name = "payment_notifications", nullable = false) private boolean paymentNotifications = true;
    @Builder.Default @Column(name = "system_alerts", nullable = false) private boolean systemAlerts = true;
    @Builder.Default @Column(name = "shift_reminders", nullable = false) private boolean shiftReminders = true;
    @Builder.Default @Column(name = "sound_enabled", nullable = false) private boolean soundEnabled = true;
    @Builder.Default @Column(name = "vibration_enabled", nullable = false) private boolean vibrationEnabled = true;
    @Builder.Default @Column(name = "quiet_hours_enabled", nullable = false) private boolean quietHoursEnabled = false;

    @Column(name = "quiet_hours_start", length = 5)
    private String quietHoursStart; // "HH:mm"

    @Column(name = "quiet_hours_end", length = 5)
    private String quietHoursEnd;   // "HH:mm"
}

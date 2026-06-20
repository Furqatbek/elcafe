package com.elcafe.modules.pos.offline.entity;

import com.elcafe.modules.pos.offline.enums.DeviceType;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Entity representing a registered POS device with offline capabilities.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pos_devices", indexes = {
    @Index(name = "idx_pos_devices_restaurant", columnList = "restaurant_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uq_pos_device", columnNames = {"restaurant_id", "device_id"})
})
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class POSDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(name = "device_name", length = 200)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 50)
    private DeviceType deviceType;

    @Column(name = "last_sync", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lastSync;

    @Column(name = "last_heartbeat", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lastHeartbeat;

    @Column(name = "offline_enabled")
    @Builder.Default
    private Boolean offlineEnabled = true;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "registered_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime registeredAt;

    public void recordHeartbeat() {
        this.lastHeartbeat = OffsetDateTime.now();
    }

    public void recordSync() {
        this.lastSync = OffsetDateTime.now();
    }

    public boolean isOnline() {
        if (lastHeartbeat == null) return false;
        // Consider device offline if no heartbeat in last 2 minutes
        return lastHeartbeat.isAfter(OffsetDateTime.now().minusMinutes(2));
    }
}

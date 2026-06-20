package com.elcafe.modules.pos.offline.entity;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.pos.offline.enums.OfflineSyncStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Entity for storing orders created while the POS terminal was offline.
 * These orders are queued for synchronization when connectivity is restored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "offline_orders", indexes = {
    @Index(name = "idx_offline_orders_sync_status", columnList = "sync_status"),
    @Index(name = "idx_offline_orders_restaurant_device", columnList = "restaurant_id, device_id"),
    @Index(name = "idx_offline_orders_created", columnList = "created_at")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uq_offline_order_client_id", columnNames = {"restaurant_id", "device_id", "client_order_id"})
})
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class OfflineOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(name = "client_order_id", nullable = false, length = 100)
    private String clientOrderId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "order_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> orderData;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 20)
    @Builder.Default
    private OfflineSyncStatus syncStatus = OfflineSyncStatus.PENDING;

    @Column(name = "sync_attempts")
    @Builder.Default
    private Integer syncAttempts = 0;

    @Column(name = "last_sync_attempt", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lastSyncAttempt;

    @Column(name = "sync_error", columnDefinition = "TEXT")
    private String syncError;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "synced_order_id")
    private Order syncedOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @Column(name = "synced_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime syncedAt;

    public void incrementSyncAttempts() {
        this.syncAttempts = (this.syncAttempts == null ? 0 : this.syncAttempts) + 1;
        this.lastSyncAttempt = OffsetDateTime.now();
    }

    public void markSynced(Order order) {
        this.syncedOrder = order;
        this.syncStatus = OfflineSyncStatus.SYNCED;
        this.syncedAt = OffsetDateTime.now();
        this.syncError = null;
    }

    public void markFailed(String error) {
        this.syncStatus = OfflineSyncStatus.FAILED;
        this.syncError = error;
    }
}

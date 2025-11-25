package com.elcafe.modules.courier.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity to track courier real-time location for delivery tracking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "courier_locations", indexes = {
        @Index(name = "idx_courier_locations_courier_id", columnList = "courier_id"),
        @Index(name = "idx_courier_locations_order_id", columnList = "order_id"),
        @Index(name = "idx_courier_locations_timestamp", columnList = "timestamp")
})
public class CourierLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id", nullable = false)
    private CourierProfile courier;

    @Column(name = "order_id")
    private Long orderId; // Optional - tracks which order this location update is for

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(length = 100)
    private String address; // Optional reverse geocoded address

    private Double speed; // Speed in km/h

    private Double accuracy; // GPS accuracy in meters

    private Double altitude; // Altitude in meters

    private Double bearing; // Direction of travel in degrees (0-360)

    @Column(name = "battery_level")
    private Integer batteryLevel; // Battery percentage (0-100)

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true; // Whether courier is currently active/online

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(length = 500)
    private String notes; // Any additional notes from courier
}

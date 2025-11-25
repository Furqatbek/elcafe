package com.elcafe.modules.order.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "delivery_info")
public class DeliveryInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 20)
    private String zipCode;

    private Double latitude;

    private Double longitude;

    @Column(length = 20)
    private String contactPhone;

    @Column(length = 200)
    private String contactName;

    @Column(length = 500)
    private String deliveryInstructions;

    private Long courierId;

    @Column(length = 200)
    private String courierName;

    @Column(length = 20)
    private String courierPhone;

    private String courierProviderId;

    private String courierTrackingId;

    private LocalDateTime pickupTime;

    private LocalDateTime estimatedDeliveryTime;

    private LocalDateTime deliveryTime;

    private LocalDateTime actualDeliveryTime;
}

package com.elcafe.modules.customer.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "customer_addresses")
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "customer"})
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(length = 200)
    private String label; // e.g., "Home", "Work", "Office"

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    // OpenStreetMap/Nominatim fields
    @Column(name = "place_id")
    private Long placeId;

    @Column(length = 20)
    private String osmType; // way, node, relation

    @Column(name = "osm_id")
    private Long osmId;

    private Double latitude;

    private Double longitude;

    @Column(length = 100)
    private String addressClass; // amenity, building, etc.

    @Column(length = 100)
    private String type; // parking, house, etc.

    @Column(columnDefinition = "TEXT")
    private String displayName;

    // Detailed address components
    @Column(length = 200)
    private String road;

    @Column(length = 200)
    private String neighbourhood;

    @Column(length = 200)
    private String county;

    @Column(length = 200)
    private String city;

    @Column(length = 200)
    private String state;

    @Column(length = 20)
    private String postcode;

    @Column(length = 100)
    private String country;

    @Column(length = 10)
    private String countryCode;

    // Bounding box (stored as JSON or separate fields)
    private Double boundingBoxMinLat;

    private Double boundingBoxMaxLat;

    private Double boundingBoxMinLon;

    private Double boundingBoxMaxLon;

    // Additional info
    @Column(columnDefinition = "TEXT")
    private String deliveryInstructions;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

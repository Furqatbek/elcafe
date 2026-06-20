package com.elcafe.modules.selfservice.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.selfservice.enums.QRCodeType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;

import java.time.LocalDateTime;

/**
 * QR code entity for self-service ordering.
 */
@Entity
@jakarta.persistence.Table(name = "qr_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class QRCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private RestaurantTable table;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "short_url", length = 255)
    private String shortUrl;

    @Column(length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "qr_type", nullable = false, length = 50)
    @Builder.Default
    private QRCodeType qrType = QRCodeType.TABLE;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "scan_count")
    @Builder.Default
    private Integer scanCount = 0;

    @Column(name = "last_scanned_at")
    private LocalDateTime lastScannedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Record a scan of this QR code.
     */
    public void recordScan() {
        this.scanCount = (this.scanCount == null ? 0 : this.scanCount) + 1;
        this.lastScannedAt = LocalDateTime.now();
    }

    /**
     * Check if this QR code is valid (active and not expired).
     */
    public boolean isValid() {
        if (!Boolean.TRUE.equals(isActive)) {
            return false;
        }
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }
}

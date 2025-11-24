package com.elcafe.modules.courier.entity;

import com.elcafe.modules.courier.enums.TariffType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "courier_tariffs")
@EntityListeners(AuditingEntityListener.class)
public class CourierTariff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TariffType type;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Fixed amount for this tariff
    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal fixedAmount = BigDecimal.ZERO;

    // Amount per order
    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal amountPerOrder = BigDecimal.ZERO;

    // Amount per kilometer
    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal amountPerKilometer = BigDecimal.ZERO;

    // Conditions for applying this tariff
    @Column
    private Integer minOrders; // Minimum orders to apply bonus

    @Column
    private Integer maxOrders; // Maximum orders before bonus stops

    @Column(precision = 5, scale = 2)
    private BigDecimal minDistance; // Minimum distance in km

    @Column(precision = 5, scale = 2)
    private BigDecimal maxDistance; // Maximum distance in km

    @Column
    private Integer minAttendanceDays; // Minimum attendance days required

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

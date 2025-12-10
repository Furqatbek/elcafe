package com.elcafe.modules.courier.entity;

import com.elcafe.modules.courier.enums.TariffType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "courier_bonus_fines")
@EntityListeners(AuditingEntityListener.class)
public class CourierBonusFine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "courier_profile_id", nullable = false)
    private CourierProfile courierProfile;

    @ManyToOne
    @JoinColumn(name = "tariff_id")
    private CourierTariff tariff;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TariffType type;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(length = 100)
    private String referenceId; // Can reference order ID, period, etc.

    @Column
    private Integer ordersCompleted; // Number of orders in the period

    @Column(precision = 10, scale = 2)
    private BigDecimal distanceCovered; // Distance in km

    @Column
    private Integer attendanceDays; // Days attended

    @Column(nullable = false)
    @Builder.Default
    private Boolean applied = false;

    @Column
    private LocalDateTime appliedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

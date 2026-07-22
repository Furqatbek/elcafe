package com.elcafe.modules.pos.scale.entity;

import com.elcafe.modules.pos.scale.enums.ScaleConnectionType;
import com.elcafe.modules.pos.scale.enums.ScaleProtocol;
import com.elcafe.modules.pos.scale.enums.WeightUnit;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Weighing scale device configuration for sell-by-weight items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "scales", indexes = {
    @Index(name = "idx_scales_restaurant", columnList = "restaurant_id")
})
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Scale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "scale_name", nullable = false, length = 100)
    private String scaleName;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", nullable = false, length = 30)
    private ScaleConnectionType connectionType;

    @Column(name = "connection_string", length = 500)
    private String connectionString;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", length = 50)
    private ScaleProtocol protocol;

    @Enumerated(EnumType.STRING)
    @Column(name = "weight_unit", length = 10)
    @Builder.Default
    private WeightUnit weightUnit = WeightUnit.KG;

    @Column(name = "decimal_places")
    @Builder.Default
    private Integer decimalPlaces = 3;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;
}

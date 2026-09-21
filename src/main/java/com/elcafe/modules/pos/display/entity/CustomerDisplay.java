package com.elcafe.modules.pos.display.entity;

import com.elcafe.modules.pos.display.enums.DisplayConnectionType;
import com.elcafe.modules.pos.display.enums.DisplayType;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Customer-facing display configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "customer_displays", indexes = {
    @Index(name = "idx_customer_displays_restaurant", columnList = "restaurant_id")
})
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CustomerDisplay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_type", nullable = false, length = 30)
    private DisplayType displayType;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", length = 30)
    private DisplayConnectionType connectionType;

    @Column(name = "connection_string", length = 500)
    private String connectionString;

    @Column(name = "show_item_details")
    @Builder.Default
    private Boolean showItemDetails = true;

    @Column(name = "show_running_total")
    @Builder.Default
    private Boolean showRunningTotal = true;

    @Column(name = "show_promotions")
    @Builder.Default
    private Boolean showPromotions = true;

    @Column(name = "idle_message", length = 500)
    private String idleMessage;

    @Column(name = "thank_you_message", length = 500)
    private String thankYouMessage;

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

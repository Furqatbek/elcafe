package com.elcafe.modules.kitchen.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.settings.entity.PrinterSettings;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "kitchen_stations", indexes = {
        @Index(name = "idx_kitchen_stations_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_kitchen_stations_active", columnList = "restaurant_id, active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"restaurant", "printer", "hibernateLazyInitializer", "handler"})
public class KitchenStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_id")
    private PrinterSettings printer;

    @Column(length = 20)
    @Builder.Default
    private String color = "#3B82F6";

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

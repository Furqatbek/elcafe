package com.elcafe.modules.menu.entity;

import com.elcafe.modules.kitchen.entity.KitchenStation;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "categories", indexes = {
        @Index(name = "idx_categories_kitchen_station", columnList = "kitchen_station_id")
})
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"restaurant", "products", "kitchenStation", "hibernateLazyInitializer", "handler"})
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Restaurant restaurant;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kitchen_station_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private KitchenStation kitchenStation;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Product> products = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Helper method to get kitchen station ID for JSON serialization
    public Long getKitchenStationId() {
        return kitchenStation != null ? kitchenStation.getId() : null;
    }

    // Helper method to get kitchen station name for JSON serialization
    public String getKitchenStationName() {
        return kitchenStation != null ? kitchenStation.getName() : null;
    }
}

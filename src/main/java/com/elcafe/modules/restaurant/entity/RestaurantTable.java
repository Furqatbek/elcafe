package com.elcafe.modules.restaurant.entity;

import com.elcafe.modules.order.entity.Order;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "restaurant_tables")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RestaurantTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 50)
    private String tableNumber;

    @Column(length = 100)
    private String tableName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TableStatus status;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "original_capacity")
    private Integer originalCapacity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_table_id")
    private RestaurantTable mergedTable;

    @Column(length = 100)
    private String section;

    // Floor plan positioning
    @Column(name = "position_x")
    private Integer positionX;

    @Column(name = "position_y")
    private Integer positionY;

    @Column(name = "table_width")
    @Builder.Default
    private Integer width = 100;

    @Column(name = "table_height")
    @Builder.Default
    private Integer height = 100;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(length = 500)
    private String notes;

    @Column(name = "qr_code")
    private String qrCode;

    @OneToMany(mappedBy = "diningTable", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Order> orders;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Version field for optimistic locking.
     * Prevents race conditions during concurrent table status updates
     * (e.g., table merging/unmerging, status changes from multiple waiters).
     */
    @Version
    private Long version;

    public enum TableStatus {
        AVAILABLE,
        OCCUPIED,
        RESERVED,
        CLEANING,
        OUT_OF_SERVICE
    }
}

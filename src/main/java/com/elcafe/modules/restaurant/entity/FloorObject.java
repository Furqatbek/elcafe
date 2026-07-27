package com.elcafe.modules.restaurant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;

/**
 * A thing on the floor that is not a table (V184): a sofa, a chair, a plant, a door, the bar itself.
 *
 * <p>Separate from {@link RestaurantTable} because the two differ in everything that matters — furniture
 * has no capacity, no status, no orders and no QR code, and nobody is ever seated "at" a plant. Folding
 * it into the table entity would mean a status column that is meaningless for most rows and an occupancy
 * projection that has to keep remembering which rows are real tables.
 */
@Entity
@Table(name = "floor_object")
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloorObject {

    /** What it is. Drives the icon and the default size the editor drops it in at. */
    public enum ObjectType { SOFA, CHAIR, PLANT, DOOR, BAR, WALL, OTHER }

    /** How the corners are drawn — the same vocabulary tables use, so the editor has one shape picker. */
    public enum Shape { RECTANGLE, ROUNDED, OVAL, SQUARE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "floor_plan_id", nullable = false)
    private Long floorPlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false, length = 30)
    private ObjectType objectType;

    @Column(length = 100)
    private String label;

    @Column(name = "position_x", nullable = false)
    @Builder.Default
    private Integer positionX = 0;

    @Column(name = "position_y", nullable = false)
    @Builder.Default
    private Integer positionY = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer width = 60;

    @Column(nullable = false)
    @Builder.Default
    private Integer height = 60;

    @Column(name = "rotation_deg", nullable = false)
    @Builder.Default
    private Integer rotationDeg = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Shape shape = Shape.RECTANGLE;

    /** Draw order — a chair tucked under a table has to be able to sit behind it. */
    @Column(name = "z_index", nullable = false)
    @Builder.Default
    private Integer zIndex = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}

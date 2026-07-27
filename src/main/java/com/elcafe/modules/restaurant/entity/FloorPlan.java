package com.elcafe.modules.restaurant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;

/**
 * One drawable map of a room (V184) — the ground floor, the upstairs hall, the summer terrace.
 *
 * <p>A restaurant has several, which is why this exists at all rather than tables belonging straight to
 * the restaurant. Objects and sections belong to a <em>plan</em>, so deleting the terrace takes its
 * furniture and drawn areas with it instead of leaving them floating on the ground floor.
 */
@Entity
@Table(name = "floor_plan")
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloorPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 100)
    private String name;

    /** Position in the map switcher. Not the id, so plans reorder without renumbering. */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    /**
     * The map opened when nothing else is remembered. At most one per restaurant — enforced by a partial
     * unique index in the database, not by application code that a second writer could race past.
     */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    /** Canvas bounds in the same abstract units as table positions — what the editor snaps and clamps to. */
    @Column(name = "canvas_width", nullable = false)
    @Builder.Default
    private Integer canvasWidth = 1200;

    @Column(name = "canvas_height", nullable = false)
    @Builder.Default
    private Integer canvasHeight = 800;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

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

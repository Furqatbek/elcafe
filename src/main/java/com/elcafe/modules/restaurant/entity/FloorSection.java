package com.elcafe.modules.restaurant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * A drawn area on a floor plan (V184) — the bar corner, a VIP room, the smoking terrace.
 *
 * <p>{@code RestaurantTable.section} already exists and stays what it is: a free-text LABEL used to
 * group tables. This is the drawn SHAPE, which a label cannot express. The two are complementary, not
 * duplicates.
 */
@Entity
@Table(name = "floor_section")
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloorSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "floor_plan_id", nullable = false)
    private Long floorPlanId;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * The outline as {@code [{"x":10,"y":20},…]}.
     *
     * <p>{@code @JdbcTypeCode(SqlTypes.JSON)} with no literal {@code columnDefinition}: that maps to
     * jsonb on Postgres and to H2's own JSON type under test, whereas hardcoding "jsonb" fails H2
     * schema generation outright.
     *
     * <p>JSON rather than PostGIS geometry because nothing here is a spatial query — the polygon is
     * drawn, hit-tested in the browser, and never intersected server-side.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Map<String, Integer>> polygon;

    @Column(name = "fill_color", length = 20)
    private String fillColor;

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

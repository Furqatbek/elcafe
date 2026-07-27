package com.elcafe.modules.restaurant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * One map, everything on it, and who is sitting where (V184).
 *
 * <p>Delivered as a single payload rather than four endpoints because the renderer cannot draw a
 * partial room: tables, furniture and sections all have to arrive before the first paint, or the map
 * flickers into existence a layer at a time. Occupancy rides along for the same reason — a plan without
 * it is just a diagram.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloorPlanView {

    private Long id;
    private String name;
    private Integer displayOrder;
    private Boolean isDefault;
    private Integer canvasWidth;
    private Integer canvasHeight;

    /** Painted back-to-front: sections underneath, then objects and tables by z-index. */
    private List<Section> sections;
    private List<FloorObjectView> objects;
    private List<TableView> tables;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Section {
        private Long id;
        private String name;
        private List<Map<String, Integer>> polygon;
        private String fillColor;
        private Integer zIndex;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FloorObjectView {
        private Long id;
        private String objectType;
        private String label;
        private Integer positionX;
        private Integer positionY;
        private Integer width;
        private Integer height;
        private Integer rotationDeg;
        private String shape;
        private Integer zIndex;
    }

    /**
     * A table as the map needs it: geometry to draw it, plus the live state that decides its colour and
     * what the drawer shows when it is clicked.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableView {
        private Long id;
        private String tableNumber;
        private String tableName;
        private Integer capacity;
        private String section;
        private String status;
        private Integer positionX;
        private Integer positionY;
        private Integer width;
        private Integer height;
        private Integer rotationDeg;
        private String shape;
        private Integer zIndex;

        /**
         * Set when this table was merged into another. The renderer needs to know so it can draw the
         * pair as one seating unit instead of two overlapping shapes claiming the same guests.
         */
        private Long mergedIntoTableId;

        /** Null when nobody is seated — the table is drawn free regardless of its stored status. */
        private Occupancy occupancy;
    }

    /**
     * Who is at the table right now, derived from the live order rather than from
     * {@code RestaurantTable.status} — the status is set by hand and drifts; an open order does not.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Occupancy {
        private Long orderId;
        private String orderNumber;
        private OffsetDateTime seatedSince;
        private java.math.BigDecimal orderTotal;

        /**
         * The guest, when they are known. Null for a walk-in — they ordered from a waiter and there is
         * no customer record behind them. The drawer shows the order alone in that case; capture happens
         * at payment, not by making somebody type at seating.
         */
        private Long customerId;
        private String customerName;
    }
}

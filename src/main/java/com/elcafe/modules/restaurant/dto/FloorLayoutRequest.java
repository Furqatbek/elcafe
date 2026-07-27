package com.elcafe.modules.restaurant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * A whole map's layout, saved in one call (V184).
 *
 * <p>Batched rather than one request per dragged shape: an edit session moves a dozen things at once and
 * the operator presses Save once, so a partial write — half the tables moved, the sections not — must
 * not be possible. One request means one transaction means one outcome.
 *
 * <p>Only geometry is accepted here. A table's number, capacity and status stay with the existing table
 * endpoints; letting the map editor rewrite them would make a stray drag capable of renaming a table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloorLayoutRequest {

    /** Tables that moved. Omitted tables are left exactly as they are, not deleted. */
    private List<TablePlacement> tables;

    /**
     * The full set of objects for this plan. Unlike tables, this IS authoritative: an object missing
     * from the list was deleted in the editor, because furniture has no life outside the map to preserve.
     */
    private List<ObjectPlacement> objects;

    /** Same contract as objects — the list replaces what was there. */
    private List<SectionShape> sections;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TablePlacement {
        private Long id;
        private Integer positionX;
        private Integer positionY;
        private Integer width;
        private Integer height;
        private Integer rotationDeg;
        private String shape;
        private Integer zIndex;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObjectPlacement {
        /** Null for something just dropped onto the canvas; set when an existing object moved. */
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionShape {
        private Long id;
        private String name;
        private List<Map<String, Integer>> polygon;
        private String fillColor;
        private Integer zIndex;
    }
}

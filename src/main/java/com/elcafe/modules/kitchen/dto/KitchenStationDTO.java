package com.elcafe.modules.kitchen.dto;

import com.elcafe.modules.kitchen.entity.KitchenStation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenStationDTO {

    private Long id;
    private Long restaurantId;
    private String name;
    private String description;
    private Long printerId;
    private String printerName;
    private String color;
    private Integer sortOrder;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static KitchenStationDTO fromEntity(KitchenStation station) {
        return KitchenStationDTO.builder()
                .id(station.getId())
                .restaurantId(station.getRestaurant() != null ? station.getRestaurant().getId() : null)
                .name(station.getName())
                .description(station.getDescription())
                .printerId(station.getPrinter() != null ? station.getPrinter().getId() : null)
                .printerName(station.getPrinter() != null ? station.getPrinter().getPrinterName() : null)
                .color(station.getColor())
                .sortOrder(station.getSortOrder())
                .active(station.getActive())
                .createdAt(station.getCreatedAt())
                .updatedAt(station.getUpdatedAt())
                .build();
    }
}

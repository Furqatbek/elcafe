package com.elcafe.modules.restaurant.dto;

import com.elcafe.modules.restaurant.entity.RestaurantTable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloorPlanDTO {

    private Long restaurantId;
    private String restaurantName;
    private List<FloorPlanTableDTO> tables;
    private List<String> sections;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FloorPlanTableDTO {
        private Long id;
        private String tableNumber;
        private String tableName;
        private RestaurantTable.TableStatus status;
        private Integer capacity;
        private String section;
        private Integer positionX;
        private Integer positionY;
        private Integer width;
        private Integer height;
        private Long currentOrderId;
        private String currentOrderNumber;
    }
}

package com.elcafe.modules.restaurant.dto;

import com.elcafe.modules.restaurant.entity.RestaurantTable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private String tableNumber;
    private String tableName;
    private RestaurantTable.TableStatus status;
    private Integer capacity;
    private String section;
    // Floor plan positioning
    private Integer positionX;
    private Integer positionY;
    private Integer width;
    private Integer height;
    private Boolean active;
    private String notes;
    private String qrCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

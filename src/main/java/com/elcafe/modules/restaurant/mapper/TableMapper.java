package com.elcafe.modules.restaurant.mapper;

import com.elcafe.modules.restaurant.dto.CreateTableRequest;
import com.elcafe.modules.restaurant.dto.TableResponse;
import com.elcafe.modules.restaurant.dto.UpdateTableRequest;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import org.springframework.stereotype.Component;

@Component
public class TableMapper {

    public RestaurantTable toEntity(CreateTableRequest request) {
        return RestaurantTable.builder()
                .tableNumber(request.getTableNumber())
                .tableName(request.getTableName())
                .status(RestaurantTable.TableStatus.AVAILABLE)
                .capacity(request.getCapacity())
                .section(request.getSection())
                .active(request.getActive() != null ? request.getActive() : true)
                .notes(request.getNotes())
                .build();
    }

    public void updateEntity(RestaurantTable table, UpdateTableRequest request) {
        if (request.getTableNumber() != null) {
            table.setTableNumber(request.getTableNumber());
        }
        if (request.getTableName() != null) {
            table.setTableName(request.getTableName());
        }
        if (request.getStatus() != null) {
            table.setStatus(request.getStatus());
        }
        if (request.getCapacity() != null) {
            table.setCapacity(request.getCapacity());
        }
        if (request.getSection() != null) {
            table.setSection(request.getSection());
        }
        if (request.getActive() != null) {
            table.setActive(request.getActive());
        }
        if (request.getNotes() != null) {
            table.setNotes(request.getNotes());
        }
    }

    public TableResponse toResponse(RestaurantTable table) {
        return TableResponse.builder()
                .id(table.getId())
                .restaurantId(table.getRestaurant().getId())
                .restaurantName(table.getRestaurant().getName())
                .tableNumber(table.getTableNumber())
                .tableName(table.getTableName())
                .status(table.getStatus())
                .capacity(table.getCapacity())
                .section(table.getSection())
                .active(table.getActive())
                .notes(table.getNotes())
                .qrCode(table.getQrCode())
                .createdAt(table.getCreatedAt())
                .updatedAt(table.getUpdatedAt())
                .build();
    }
}

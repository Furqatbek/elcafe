package com.elcafe.modules.menu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuCollectionDTO {
    private Long id;
    private Long restaurantId;
    private String name;
    private String description;
    private String imageUrl;
    private Boolean isActive;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer sortOrder;
    private List<MenuCollectionItemDTO> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.elcafe.modules.menu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddOnGroupResponse {
    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private String name;
    private String description;
    private Boolean required;
    private Integer minSelection;
    private Integer maxSelection;
    private Boolean active;
    private List<AddOnResponse> addOns;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

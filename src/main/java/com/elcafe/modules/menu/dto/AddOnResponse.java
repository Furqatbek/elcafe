package com.elcafe.modules.menu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddOnResponse {
    private Long id;
    private Long addOnGroupId;
    private String addOnGroupName;
    private String name;
    private String description;
    private BigDecimal price;
    private Boolean available;
    private Integer sortOrder;
}

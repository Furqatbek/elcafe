package com.elcafe.modules.menu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for public menu category with products
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicMenuCategoryDTO {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private Integer sortOrder;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<PublicMenuProductDTO> products = new ArrayList<>();
}

package com.elcafe.modules.menu.dto;

import com.elcafe.modules.menu.enums.LinkType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkedItemDTO {
    private Long id;
    private Long productId;
    private Long linkedProductId;
    private String linkedProductName;
    private String linkedProductImageUrl;
    private LinkType linkType;
    private Integer sortOrder;
}

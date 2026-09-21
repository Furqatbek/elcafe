package com.elcafe.modules.menu.dto;

import com.elcafe.modules.menu.enums.LinkType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddLinkedItemRequest {

    @NotNull(message = "Linked product ID is required")
    private Long linkedProductId;

    @NotNull(message = "Link type is required")
    private LinkType linkType;

    private Integer sortOrder;
}

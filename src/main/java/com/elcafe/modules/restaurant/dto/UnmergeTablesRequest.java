package com.elcafe.modules.restaurant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnmergeTablesRequest {

    @NotNull(message = "Table ID is required")
    private Long tableId;
}

package com.elcafe.modules.restaurant.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeTablesRequest {

    @NotNull(message = "Main table ID is required")
    private Long mainTableId;

    @NotEmpty(message = "At least one table to merge is required")
    @Size(min = 1, message = "At least one table must be merged")
    private List<Long> tableIdsToMerge;
}

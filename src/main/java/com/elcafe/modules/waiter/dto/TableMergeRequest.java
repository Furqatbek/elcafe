package com.elcafe.modules.waiter.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableMergeRequest {

    @NotNull(message = "Target table ID is required")
    private Long targetTableId;
}

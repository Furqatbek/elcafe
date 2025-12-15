package com.elcafe.modules.waiter.dto;

import com.elcafe.modules.waiter.enums.TableStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTableRequest {

    @Min(value = 1, message = "Table number must be at least 1")
    private Integer number;

    @Min(value = 1, message = "Capacity must be at least 1")
    @Max(value = 50, message = "Capacity must not exceed 50")
    private Integer capacity;

    @Size(max = 50, message = "Floor must not exceed 50 characters")
    private String floor;

    @Size(max = 50, message = "Section must not exceed 50 characters")
    private String section;

    private TableStatus status;
}

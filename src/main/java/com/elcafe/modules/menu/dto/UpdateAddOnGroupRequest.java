package com.elcafe.modules.menu.dto;

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
public class UpdateAddOnGroupRequest {

    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private Boolean required;

    @Min(value = 0, message = "Minimum selection must be 0 or greater")
    private Integer minSelection;

    @Min(value = 1, message = "Maximum selection must be 1 or greater")
    private Integer maxSelection;

    private Boolean active;
}

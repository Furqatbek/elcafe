package com.elcafe.modules.menu.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAddOnGroupRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Builder.Default
    private Boolean required = false;

    @Min(value = 0, message = "Minimum selection must be 0 or greater")
    @Builder.Default
    private Integer minSelection = 0;

    @Min(value = 1, message = "Maximum selection must be 1 or greater")
    @Builder.Default
    private Integer maxSelection = 1;

    @Builder.Default
    private Boolean active = true;
}

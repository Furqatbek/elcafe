package com.elcafe.modules.pos.cashdrawer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCashDrawerRequest {

    @NotBlank(message = "Drawer name is required")
    private String drawerName;

    private String deviceId;

    private String printerName;

    @Builder.Default
    private String kickCommand = "ESC_P";

    @Builder.Default
    private BigDecimal expectedFloat = BigDecimal.ZERO;
}

package com.elcafe.modules.courier.dto;

import com.elcafe.modules.courier.enums.TariffType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Courier tariff response")
public class CourierTariffResponse {

    @Schema(description = "Tariff ID", example = "1")
    private Long id;

    @Schema(description = "Name of the tariff", example = "Weekend Bonus")
    private String name;

    @Schema(description = "Type of tariff", example = "BONUS")
    private TariffType type;

    @Schema(description = "Description of the tariff", example = "Bonus for working on weekends")
    private String description;

    @Schema(description = "Fixed amount for this tariff", example = "50.00")
    private BigDecimal fixedAmount;

    @Schema(description = "Amount per order", example = "5.00")
    private BigDecimal amountPerOrder;

    @Schema(description = "Amount per kilometer", example = "2.50")
    private BigDecimal amountPerKilometer;

    @Schema(description = "Minimum orders required", example = "10")
    private Integer minOrders;

    @Schema(description = "Maximum orders", example = "50")
    private Integer maxOrders;

    @Schema(description = "Minimum distance in km", example = "5.0")
    private BigDecimal minDistance;

    @Schema(description = "Maximum distance in km", example = "20.0")
    private BigDecimal maxDistance;

    @Schema(description = "Minimum attendance days required", example = "5")
    private Integer minAttendanceDays;

    @Schema(description = "Whether the tariff is active", example = "true")
    private Boolean active;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}

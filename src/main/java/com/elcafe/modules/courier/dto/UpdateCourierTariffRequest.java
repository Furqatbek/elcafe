package com.elcafe.modules.courier.dto;

import com.elcafe.modules.courier.enums.TariffType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update an existing courier tariff")
public class UpdateCourierTariffRequest {

    @Size(max = 200, message = "Name cannot exceed 200 characters")
    @Schema(description = "Name of the tariff", example = "Weekend Bonus")
    private String name;

    @Schema(description = "Type of tariff (BONUS or FINE)", example = "BONUS")
    private TariffType type;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Schema(description = "Description of the tariff", example = "Bonus for working on weekends")
    private String description;

    @DecimalMin(value = "0.0", message = "Fixed amount must be non-negative")
    @Schema(description = "Fixed amount for this tariff", example = "50.00")
    private BigDecimal fixedAmount;

    @DecimalMin(value = "0.0", message = "Amount per order must be non-negative")
    @Schema(description = "Amount per order", example = "5.00")
    private BigDecimal amountPerOrder;

    @DecimalMin(value = "0.0", message = "Amount per kilometer must be non-negative")
    @Schema(description = "Amount per kilometer", example = "2.50")
    private BigDecimal amountPerKilometer;

    @Schema(description = "Minimum orders required to apply this tariff", example = "10")
    private Integer minOrders;

    @Schema(description = "Maximum orders before tariff stops applying", example = "50")
    private Integer maxOrders;

    @DecimalMin(value = "0.0", message = "Minimum distance must be non-negative")
    @Schema(description = "Minimum distance in km", example = "5.0")
    private BigDecimal minDistance;

    @DecimalMin(value = "0.0", message = "Maximum distance must be non-negative")
    @Schema(description = "Maximum distance in km", example = "20.0")
    private BigDecimal maxDistance;

    @Schema(description = "Minimum attendance days required", example = "5")
    private Integer minAttendanceDays;

    @Schema(description = "Whether the tariff is active", example = "true")
    private Boolean active;
}

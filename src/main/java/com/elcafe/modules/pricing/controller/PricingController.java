package com.elcafe.modules.pricing.controller;

import com.elcafe.modules.pricing.dto.PricingAnalyticsDTO;
import com.elcafe.modules.pricing.dto.PricingRecommendationDTO;
import com.elcafe.modules.pricing.dto.ProductProfitabilityDTO;
import com.elcafe.modules.pricing.service.PricingStrategyService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
@Tag(name = "Pricing", description = "Pricing strategy and analytics APIs")
public class PricingController {

    private final PricingStrategyService pricingStrategyService;

    @GetMapping("/analytics/{restaurantId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get pricing analytics", description = "Get comprehensive pricing analytics for a restaurant")
    public ResponseEntity<ApiResponse<PricingAnalyticsDTO>> getPricingAnalytics(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) BigDecimal targetMargin
    ) {
        log.info("Fetching pricing analytics for restaurant: {}", restaurantId);

        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            startDate = endDate.minusDays(30);
        }

        PricingAnalyticsDTO analytics = pricingStrategyService.getPricingAnalytics(
                restaurantId, startDate, endDate, targetMargin
        );

        return ResponseEntity.ok(ApiResponse.success("Pricing analytics retrieved successfully", analytics));
    }

    @GetMapping("/recommendations/{restaurantId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get pricing recommendations", description = "Get pricing recommendations for all products")
    public ResponseEntity<ApiResponse<List<PricingRecommendationDTO>>> getPricingRecommendations(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) BigDecimal targetMargin
    ) {
        log.info("Generating pricing recommendations for restaurant: {}", restaurantId);

        List<PricingRecommendationDTO> recommendations = pricingStrategyService.generatePricingRecommendations(
                restaurantId, targetMargin
        );

        return ResponseEntity.ok(ApiResponse.success("Pricing recommendations generated successfully", recommendations));
    }

    @GetMapping("/profitability/{restaurantId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all products profitability", description = "Get profitability analysis for all products")
    public ResponseEntity<ApiResponse<List<ProductProfitabilityDTO>>> getAllProductsProfitability(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        log.info("Fetching products profitability for restaurant: {}", restaurantId);

        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            startDate = endDate.minusDays(30);
        }

        List<ProductProfitabilityDTO> profitability = pricingStrategyService.getAllProductsProfitability(
                restaurantId, startDate, endDate
        );

        return ResponseEntity.ok(ApiResponse.success("Products profitability retrieved successfully", profitability));
    }

    @GetMapping("/profitability/{restaurantId}/product/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get product profitability", description = "Get detailed profitability analysis for a specific product")
    public ResponseEntity<ApiResponse<ProductProfitabilityDTO>> getProductProfitability(
            @PathVariable Long restaurantId,
            @PathVariable Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        log.info("Fetching profitability for product: {} in restaurant: {}", productId, restaurantId);

        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            startDate = endDate.minusDays(30);
        }

        ProductProfitabilityDTO profitability = pricingStrategyService.getProductProfitability(
                productId, startDate, endDate
        );

        return ResponseEntity.ok(ApiResponse.success("Product profitability retrieved successfully", profitability));
    }

    @GetMapping("/calculate/cost-plus")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Calculate cost-plus price", description = "Calculate price based on cost and target margin")
    public ResponseEntity<ApiResponse<BigDecimal>> calculateCostPlusPrice(
            @RequestParam BigDecimal costPrice,
            @RequestParam BigDecimal targetMarginPercentage
    ) {
        log.info("Calculating cost-plus price for cost: {}, margin: {}%", costPrice, targetMarginPercentage);

        BigDecimal price = pricingStrategyService.calculateCostPlusPrice(costPrice, targetMarginPercentage);

        return ResponseEntity.ok(ApiResponse.success("Price calculated successfully", price));
    }

    @GetMapping("/calculate/psychological")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Apply psychological pricing", description = "Apply psychological pricing (e.g., $9.99 instead of $10)")
    public ResponseEntity<ApiResponse<BigDecimal>> applyPsychologicalPricing(
            @RequestParam BigDecimal price
    ) {
        log.info("Applying psychological pricing to: {}", price);

        BigDecimal adjustedPrice = pricingStrategyService.applyPsychologicalPricing(price);

        return ResponseEntity.ok(ApiResponse.success("Psychological pricing applied", adjustedPrice));
    }
}

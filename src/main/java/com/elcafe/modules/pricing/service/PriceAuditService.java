package com.elcafe.modules.pricing.service;

import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.pricing.entity.PriceChangeLog;
import com.elcafe.modules.pricing.enums.PricingStrategy;
import com.elcafe.modules.pricing.repository.PriceChangeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for auditing price changes
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAuditService {

    private final PriceChangeLogRepository priceChangeLogRepository;

    /**
     * Log a price change for a product
     */
    public PriceChangeLog logPriceChange(
            Product product,
            BigDecimal previousPrice,
            BigDecimal newPrice,
            BigDecimal previousCostPrice,
            BigDecimal newCostPrice,
            PricingStrategy strategy,
            String reason,
            String changedBy,
            boolean isSystemGenerated,
            Boolean recommendationAccepted
    ) {
        BigDecimal priceChange = newPrice.subtract(previousPrice);
        BigDecimal priceChangePct = previousPrice.compareTo(BigDecimal.ZERO) > 0
                ? priceChange.divide(previousPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal prevMarginPct = calculateMarginPercentage(previousPrice, previousCostPrice);
        BigDecimal newMarginPct = calculateMarginPercentage(newPrice, newCostPrice);

        PriceChangeLog changeLog = PriceChangeLog.builder()
                .restaurantId(product.getCategory().getRestaurant().getId())
                .productId(product.getId())
                .productName(product.getName())
                .previousPrice(previousPrice)
                .newPrice(newPrice)
                .priceChange(priceChange)
                .priceChangePercentage(priceChangePct)
                .previousCostPrice(previousCostPrice)
                .newCostPrice(newCostPrice)
                .previousMarginPercentage(prevMarginPct)
                .newMarginPercentage(newMarginPct)
                .pricingStrategy(strategy)
                .changeReason(reason)
                .changedBy(changedBy)
                .isSystemGenerated(isSystemGenerated)
                .recommendationAccepted(recommendationAccepted)
                .build();

        PriceChangeLog saved = priceChangeLogRepository.save(changeLog);
        log.info("Logged price change for product {}: {} -> {} ({}% change)",
                product.getName(), previousPrice, newPrice, priceChangePct);

        return saved;
    }

    /**
     * Get price change history for a product
     */
    public List<PriceChangeLog> getProductPriceHistory(Long productId) {
        return priceChangeLogRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    /**
     * Get recent price changes for a restaurant
     */
    public List<PriceChangeLog> getRecentPriceChanges(Long restaurantId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return priceChangeLogRepository.findRecentByRestaurantId(restaurantId, since);
    }

    /**
     * Get price change statistics for a restaurant
     */
    public PriceChangeStats getPriceChangeStats(Long restaurantId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        Long acceptedCount = priceChangeLogRepository.countAcceptedRecommendations(restaurantId, since);
        Double avgChangePct = priceChangeLogRepository.getAveragePriceChangePercentage(restaurantId, since);

        List<PriceChangeLog> changes = priceChangeLogRepository.findRecentByRestaurantId(restaurantId, since);

        long increaseCount = changes.stream()
                .filter(c -> c.getPriceChange().compareTo(BigDecimal.ZERO) > 0)
                .count();

        long decreaseCount = changes.stream()
                .filter(c -> c.getPriceChange().compareTo(BigDecimal.ZERO) < 0)
                .count();

        return PriceChangeStats.builder()
                .totalChanges(changes.size())
                .priceIncreases((int) increaseCount)
                .priceDecreases((int) decreaseCount)
                .recommendationsAccepted(acceptedCount != null ? acceptedCount.intValue() : 0)
                .averageChangePercentage(avgChangePct != null ? BigDecimal.valueOf(avgChangePct) : BigDecimal.ZERO)
                .build();
    }

    private BigDecimal calculateMarginPercentage(BigDecimal price, BigDecimal cost) {
        if (price == null || cost == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return price.subtract(cost)
                .divide(price, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PriceChangeStats {
        private int totalChanges;
        private int priceIncreases;
        private int priceDecreases;
        private int recommendationsAccepted;
        private BigDecimal averageChangePercentage;
    }
}

package com.elcafe.modules.inventory.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.inventory.dto.StockCountRequest;
import com.elcafe.modules.inventory.dto.VarianceReportResponse;
import com.elcafe.modules.inventory.entity.*;
import com.elcafe.modules.inventory.repository.*;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCountService {

    private final StockCountRepository stockCountRepository;
    private final StockCountItemRepository stockCountItemRepository;
    private final StockVarianceHistoryRepository varianceHistoryRepository;
    private final InventoryIngredientRepository ingredientRepository;
    private final RestaurantRepository restaurantRepository;
    private final InventoryService inventoryService;

    /**
     * Create a new stock count
     */
    @Transactional
    public StockCount createStockCount(StockCountRequest request) {
        log.info("Creating stock count for restaurant: {}", request.getRestaurantId());

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        String countNumber = generateCountNumber(request.getRestaurantId());

        StockCount stockCount = StockCount.builder()
                .restaurant(restaurant)
                .countNumber(countNumber)
                .countType(request.getCountType())
                .status(StockCount.Status.DRAFT)
                .scheduledDate(request.getScheduledDate())
                .initiatedBy(request.getInitiatedBy())
                .notes(request.getNotes())
                .totalItems(0)
                .countedItems(0)
                .varianceCount(0)
                .totalVarianceValue(BigDecimal.ZERO)
                .build();

        StockCount savedStockCount = stockCountRepository.save(stockCount);

        // Add items based on count type
        List<Ingredient> ingredientsToCount;
        if (request.getCountType() == StockCount.CountType.FULL) {
            // Full count - all active ingredients
            ingredientsToCount = ingredientRepository.findByRestaurant_IdAndActiveTrue(request.getRestaurantId());
        } else if (request.getIngredientIds() != null && !request.getIngredientIds().isEmpty()) {
            // Cycle or spot check - specific ingredients
            ingredientsToCount = ingredientRepository.findAllById(request.getIngredientIds());
        } else {
            ingredientsToCount = new ArrayList<>();
        }

        for (Ingredient ingredient : ingredientsToCount) {
            StockCountItem item = StockCountItem.builder()
                    .stockCount(savedStockCount)
                    .ingredient(ingredient)
                    .systemQuantity(ingredient.getCurrentStock())
                    .status(StockCountItem.Status.PENDING)
                    .build();
            savedStockCount.addItem(item);
        }

        savedStockCount.setTotalItems(ingredientsToCount.size());
        StockCount result = stockCountRepository.save(savedStockCount);

        log.info("Stock count created: {} with {} items", countNumber, ingredientsToCount.size());
        return result;
    }

    /**
     * Start a stock count (change from DRAFT to IN_PROGRESS)
     */
    @Transactional
    public StockCount startStockCount(Long stockCountId, String countedBy) {
        StockCount stockCount = stockCountRepository.findById(stockCountId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock count not found"));

        if (stockCount.getStatus() != StockCount.Status.DRAFT) {
            throw new BadRequestException("Only draft stock counts can be started");
        }

        stockCount.setStatus(StockCount.Status.IN_PROGRESS);
        stockCount.setStartedAt(LocalDateTime.now());
        stockCount.setCountedBy(countedBy);

        log.info("Stock count started: {}", stockCount.getCountNumber());
        return stockCountRepository.save(stockCount);
    }

    /**
     * Record a count for an item
     */
    @Transactional
    public StockCountItem recordCount(StockCountRequest.RecordCountRequest request) {
        StockCountItem item = stockCountItemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Stock count item not found"));

        StockCount stockCount = item.getStockCount();
        if (stockCount.getStatus() != StockCount.Status.IN_PROGRESS) {
            throw new BadRequestException("Can only record counts for in-progress stock counts");
        }

        item.recordCount(request.getCountedQuantity(), request.getCountedBy(), request.getNotes());
        StockCountItem savedItem = stockCountItemRepository.save(item);

        // Recalculate stock count totals
        stockCount.recalculateTotals();
        stockCountRepository.save(stockCount);

        log.info("Count recorded for item {} in stock count {}: system={}, counted={}, variance={}",
                item.getIngredient().getName(), stockCount.getCountNumber(),
                item.getSystemQuantity(), item.getCountedQuantity(), item.getVarianceQuantity());

        return savedItem;
    }

    /**
     * Set variance reason for an item
     */
    @Transactional
    public StockCountItem setVarianceReason(StockCountRequest.VarianceReasonRequest request) {
        StockCountItem item = stockCountItemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Stock count item not found"));

        item.setVarianceReason(request.getVarianceReason());
        if (request.getNotes() != null) {
            item.setNotes(request.getNotes());
        }

        log.info("Variance reason set for item {}: {}", item.getIngredient().getName(), request.getVarianceReason());
        return stockCountItemRepository.save(item);
    }

    /**
     * Submit stock count for review
     */
    @Transactional
    public StockCount submitForReview(Long stockCountId, String reviewedBy) {
        StockCount stockCount = stockCountRepository.findById(stockCountId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock count not found"));

        if (stockCount.getStatus() != StockCount.Status.IN_PROGRESS) {
            throw new BadRequestException("Only in-progress stock counts can be submitted for review");
        }

        if (!stockCount.isFullyCounted()) {
            throw new BadRequestException("All items must be counted before submitting for review");
        }

        stockCount.setStatus(StockCount.Status.PENDING_REVIEW);
        stockCount.setReviewedBy(reviewedBy);
        stockCount.setCompletedAt(LocalDateTime.now());

        log.info("Stock count submitted for review: {}", stockCount.getCountNumber());
        return stockCountRepository.save(stockCount);
    }

    /**
     * Approve stock count and optionally adjust inventory
     */
    @Transactional
    public StockCount approveStockCount(Long stockCountId, StockCountRequest.ApproveRequest request) {
        StockCount stockCount = stockCountRepository.findById(stockCountId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock count not found"));

        if (stockCount.getStatus() != StockCount.Status.PENDING_REVIEW) {
            throw new BadRequestException("Only pending review stock counts can be approved");
        }

        stockCount.setStatus(StockCount.Status.APPROVED);
        stockCount.setApprovedBy(request.getApprovedBy());
        stockCount.setApprovedAt(LocalDateTime.now());

        if (request.getNotes() != null) {
            stockCount.setNotes(stockCount.getNotes() + "\nApproval notes: " + request.getNotes());
        }

        // Record variance history and optionally adjust inventory
        for (StockCountItem item : stockCount.getItems()) {
            // Record variance history
            if (item.getVarianceQuantity() != null && item.getVarianceQuantity().compareTo(BigDecimal.ZERO) != 0) {
                StockVarianceHistory history = StockVarianceHistory.fromStockCountItem(item);
                history.setAdjustmentMade(request.isAdjustInventory());
                varianceHistoryRepository.save(history);
            }

            // Adjust inventory if requested
            if (request.isAdjustInventory() && item.getCountedQuantity() != null) {
                inventoryService.adjustStock(
                        item.getIngredient().getId(),
                        item.getCountedQuantity(),
                        "Stock Count Adjustment: " + stockCount.getCountNumber(),
                        request.getApprovedBy()
                );
            }
        }

        log.info("Stock count approved: {} (inventory adjusted: {})",
                stockCount.getCountNumber(), request.isAdjustInventory());
        return stockCountRepository.save(stockCount);
    }

    /**
     * Cancel a stock count
     */
    @Transactional
    public StockCount cancelStockCount(Long stockCountId, String reason, String cancelledBy) {
        StockCount stockCount = stockCountRepository.findById(stockCountId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock count not found"));

        if (stockCount.getStatus() == StockCount.Status.APPROVED) {
            throw new BadRequestException("Approved stock counts cannot be cancelled");
        }

        stockCount.setStatus(StockCount.Status.CANCELLED);
        stockCount.setNotes(stockCount.getNotes() + "\nCancelled by " + cancelledBy + ": " + reason);

        log.info("Stock count cancelled: {}", stockCount.getCountNumber());
        return stockCountRepository.save(stockCount);
    }

    /**
     * Get stock count by ID with items
     */
    @Transactional(readOnly = true)
    public StockCount getStockCountById(Long id) {
        return stockCountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock count not found"));
    }

    /**
     * Get all stock counts for a restaurant
     */
    @Transactional(readOnly = true)
    public List<StockCount> getStockCountsByRestaurant(Long restaurantId) {
        return stockCountRepository.findByRestaurant_Id(restaurantId);
    }

    /**
     * Get active (non-approved, non-cancelled) stock counts
     */
    @Transactional(readOnly = true)
    public List<StockCount> getActiveStockCounts(Long restaurantId) {
        return stockCountRepository.findActiveStockCounts(restaurantId);
    }

    /**
     * Get items with variance for a stock count
     */
    @Transactional(readOnly = true)
    public List<StockCountItem> getItemsWithVariance(Long stockCountId) {
        return stockCountItemRepository.findItemsWithVariance(stockCountId);
    }

    /**
     * Generate variance report for date range
     */
    @Transactional(readOnly = true)
    public VarianceReportResponse generateVarianceReport(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        BigDecimal totalValue = varianceHistoryRepository.getTotalVarianceValueByDateRange(restaurantId, startDate, endDate);

        List<Object[]> byReason = varianceHistoryRepository.getVarianceBreakdownByReason(restaurantId, startDate, endDate);
        List<VarianceReportResponse.VarianceByReason> reasonBreakdown = byReason.stream()
                .map(row -> VarianceReportResponse.VarianceByReason.builder()
                        .reason(row[0] != null ? row[0].toString() : "UNKNOWN")
                        .count((Long) row[1])
                        .totalValue((BigDecimal) row[2])
                        .build())
                .collect(Collectors.toList());

        List<Object[]> topIngredients = varianceHistoryRepository.getTopVarianceIngredients(
                restaurantId, startDate, endDate, PageRequest.of(0, 10));
        List<VarianceReportResponse.TopVarianceIngredient> ingredientBreakdown = topIngredients.stream()
                .map(row -> VarianceReportResponse.TopVarianceIngredient.builder()
                        .ingredientId((Long) row[0])
                        .ingredientName((String) row[1])
                        .varianceCount((Long) row[2])
                        .totalVarianceValue((BigDecimal) row[3])
                        .build())
                .collect(Collectors.toList());

        return VarianceReportResponse.builder()
                .restaurantId(restaurantId)
                .startDate(startDate)
                .endDate(endDate)
                .totalVarianceValue(totalValue)
                .totalVarianceCount(reasonBreakdown.stream().mapToInt(r -> r.getCount().intValue()).sum())
                .varianceByReason(reasonBreakdown)
                .topVarianceIngredients(ingredientBreakdown)
                .build();
    }

    private String generateCountNumber(Long restaurantId) {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = stockCountRepository.countByRestaurantIdAndCountNumberPrefix(restaurantId, "SC-" + datePrefix);
        return String.format("SC-%s-%04d", datePrefix, count + 1);
    }
}

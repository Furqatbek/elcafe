package com.elcafe.modules.inventory.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.modules.inventory.dto.WasteRecordRequest;
import com.elcafe.modules.inventory.dto.WasteReportResponse;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.entity.WasteRecord;
import com.elcafe.modules.inventory.repository.InventoryBatchRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.WasteRecordRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WasteService {

    private final WasteRecordRepository wasteRecordRepository;
    private final InventoryIngredientRepository ingredientRepository;
    private final InventoryBatchRepository batchRepository;
    private final RestaurantRepository restaurantRepository;
    private final InventoryService inventoryService;

    /**
     * Record a waste event with cost tracking
     * Cost priority: 1) Provided unitCost, 2) Batch cost, 3) Ingredient effective cost (WAC)
     */
    @Transactional
    public WasteRecord recordWaste(WasteRecordRequest request) {
        log.info("Recording waste for ingredient {} in restaurant {}",
                request.getIngredientId(), request.getRestaurantId());

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        Ingredient ingredient = ingredientRepository.findById(request.getIngredientId())
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found"));

        InventoryBatch batch = null;
        if (request.getBatchId() != null) {
            batch = batchRepository.findById(request.getBatchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Batch not found"));
        }

        // Determine unit cost using priority: provided > batch > ingredient effective cost (WAC)
        BigDecimal unitCost = request.getUnitCost();
        if (unitCost == null && batch != null && batch.getCostPerUnit() != null) {
            unitCost = batch.getCostPerUnit();
        }
        if (unitCost == null) {
            unitCost = ingredient.getEffectiveCost();
        }

        WasteRecord wasteRecord = WasteRecord.builder()
                .restaurant(restaurant)
                .ingredient(ingredient)
                .batch(batch)
                .wasteDate(request.getWasteDate() != null ? request.getWasteDate() : LocalDate.now())
                .quantity(request.getQuantity())
                .unitCost(unitCost)
                .wasteReason(request.getWasteReason())
                .recordedBy(request.getRecordedBy())
                .notes(request.getNotes())
                .build();

        WasteRecord savedRecord = wasteRecordRepository.save(wasteRecord);

        // Deduct from inventory (adjustStock now tracks cost)
        try {
            inventoryService.adjustStock(
                    ingredient.getId(),
                    ingredient.getCurrentStock().subtract(request.getQuantity()),
                    "Waste: " + request.getWasteReason().getLabel() +
                            (request.getNotes() != null ? " - " + request.getNotes() : ""),
                    request.getRecordedBy()
            );
        } catch (Exception e) {
            log.warn("Failed to deduct waste from inventory: {}", e.getMessage());
        }

        log.info("Waste recorded: {} {} of {} at {} per unit (reason: {}, total cost: {})",
                request.getQuantity(), ingredient.getUnit(), ingredient.getName(),
                unitCost, request.getWasteReason().getLabel(), savedRecord.getTotalCost());

        return savedRecord;
    }

    /**
     * Record waste from a batch write-off (called by InventoryBatchService)
     */
    @Transactional
    public WasteRecord recordWasteFromBatch(InventoryBatch batch, WasteRecord.WasteReason reason,
                                            String recordedBy, String notes) {
        log.info("Recording waste from batch write-off: {}", batch.getBatchNumber());

        WasteRecord wasteRecord = WasteRecord.builder()
                .restaurant(batch.getIngredient().getRestaurant())
                .ingredient(batch.getIngredient())
                .batch(batch)
                .wasteDate(LocalDate.now())
                .quantity(batch.getQuantity())
                .unitCost(batch.getCostPerUnit())
                .wasteReason(reason)
                .recordedBy(recordedBy)
                .notes(notes)
                .build();

        return wasteRecordRepository.save(wasteRecord);
    }

    /**
     * Get all waste records for a restaurant
     */
    @Transactional(readOnly = true)
    public List<WasteRecord> getWasteRecords(Long restaurantId) {
        return wasteRecordRepository.findByRestaurant_Id(restaurantId);
    }

    /**
     * Get waste records with filters
     */
    @Transactional(readOnly = true)
    public List<WasteRecord> getWasteRecords(Long restaurantId, LocalDate startDate, LocalDate endDate,
                                              WasteRecord.WasteReason reason, Long ingredientId) {
        if (ingredientId != null) {
            return wasteRecordRepository.findByRestaurant_IdAndIngredientIdAndDateRange(
                    restaurantId, ingredientId, startDate, endDate);
        } else if (reason != null) {
            return wasteRecordRepository.findByRestaurant_IdAndDateRangeAndReason(
                    restaurantId, startDate, endDate, reason);
        } else {
            return wasteRecordRepository.findByRestaurant_IdAndDateRange(restaurantId, startDate, endDate);
        }
    }

    /**
     * Get waste record by ID
     */
    @Transactional(readOnly = true)
    public WasteRecord getWasteRecordById(Long id) {
        return wasteRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Waste record not found"));
    }

    /**
     * Delete a waste record
     */
    @Transactional
    public void deleteWasteRecord(Long id) {
        WasteRecord record = wasteRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Waste record not found"));

        // Optionally restore inventory (commented out - might not always be desired)
        // inventoryService.addStock(record.getIngredient().getId(), record.getQuantity(),
        //         "Waste record deleted", "SYSTEM");

        wasteRecordRepository.delete(record);
        log.info("Waste record deleted: {}", id);
    }

    /**
     * Generate waste report for date range
     */
    @Transactional(readOnly = true)
    public WasteReportResponse generateWasteReport(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        log.info("Generating waste report for restaurant {} from {} to {}", restaurantId, startDate, endDate);

        // Summary statistics
        BigDecimal totalCost = wasteRecordRepository.getTotalWasteCost(restaurantId, startDate, endDate);
        BigDecimal totalQuantity = wasteRecordRepository.getTotalWasteQuantity(restaurantId, startDate, endDate);
        Long recordCount = wasteRecordRepository.getWasteRecordCount(restaurantId, startDate, endDate);
        WasteRecord.WasteReason mostCommon = wasteRecordRepository.getMostCommonWasteReason(restaurantId, startDate, endDate);

        // Breakdown by reason
        List<Object[]> byReasonData = wasteRecordRepository.getWasteBreakdownByReason(restaurantId, startDate, endDate);
        List<WasteReportResponse.WasteByReason> wasteByReason = new ArrayList<>();
        for (Object[] row : byReasonData) {
            WasteRecord.WasteReason reason = (WasteRecord.WasteReason) row[0];
            Long count = (Long) row[1];
            BigDecimal quantity = (BigDecimal) row[2];
            BigDecimal cost = (BigDecimal) row[3];

            BigDecimal percentage = totalCost.compareTo(BigDecimal.ZERO) > 0
                    ? cost.multiply(BigDecimal.valueOf(100)).divide(totalCost, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            wasteByReason.add(WasteReportResponse.WasteByReason.builder()
                    .reason(reason.name())
                    .reasonLabel(reason.getLabel())
                    .recordCount(count)
                    .totalQuantity(quantity)
                    .totalCost(cost)
                    .percentageOfTotal(percentage)
                    .build());
        }

        // Top wasted ingredients
        List<Object[]> topIngredientsData = wasteRecordRepository.getTopWastedIngredients(
                restaurantId, startDate, endDate, PageRequest.of(0, 10));
        List<WasteReportResponse.TopWastedIngredient> topWastedIngredients = topIngredientsData.stream()
                .map(row -> WasteReportResponse.TopWastedIngredient.builder()
                        .ingredientId((Long) row[0])
                        .ingredientName((String) row[1])
                        .recordCount((Long) row[2])
                        .totalQuantity((BigDecimal) row[3])
                        .totalCost((BigDecimal) row[4])
                        .build())
                .collect(Collectors.toList());

        // Daily trend
        List<Object[]> dailyData = wasteRecordRepository.getDailyWasteTotals(restaurantId, startDate, endDate);
        List<WasteReportResponse.DailyWaste> dailyTrend = dailyData.stream()
                .map(row -> WasteReportResponse.DailyWaste.builder()
                        .date((LocalDate) row[0])
                        .recordCount((Long) row[1])
                        .totalCost((BigDecimal) row[2])
                        .build())
                .collect(Collectors.toList());

        return WasteReportResponse.builder()
                .restaurantId(restaurantId)
                .startDate(startDate)
                .endDate(endDate)
                .totalWasteCost(totalCost)
                .totalWasteQuantity(totalQuantity)
                .recordCount(recordCount)
                .mostCommonReason(mostCommon != null ? mostCommon.getLabel() : null)
                .wasteByReason(wasteByReason)
                .topWastedIngredients(topWastedIngredients)
                .dailyTrend(dailyTrend)
                .build();
    }
}

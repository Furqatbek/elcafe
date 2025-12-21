package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.dto.BatchRequest;
import com.elcafe.modules.inventory.dto.BatchResponse;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.entity.Supplier;
import com.elcafe.modules.inventory.entity.WasteRecord;
import com.elcafe.modules.inventory.repository.InventoryBatchRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryBatchService {

    private final InventoryBatchRepository batchRepository;
    private final InventoryIngredientRepository ingredientRepository;
    private final SupplierRepository supplierRepository;
    private final WasteService wasteService;

    /**
     * Create a new batch for an ingredient
     */
    @Transactional
    public InventoryBatch createBatch(BatchRequest request) {
        log.info("Creating batch for ingredient: {}", request.getIngredientId());

        Ingredient ingredient = ingredientRepository.findById(request.getIngredientId())
                .orElseThrow(() -> new RuntimeException("Ingredient not found"));

        // Generate batch number if not provided
        String batchNumber = request.getBatchNumber();
        if (batchNumber == null || batchNumber.isBlank()) {
            batchNumber = generateBatchNumber(ingredient.getId());
        }

        // Check for duplicate batch number
        if (batchRepository.findByIngredientIdAndBatchNumber(ingredient.getId(), batchNumber).isPresent()) {
            throw new RuntimeException("Batch number already exists for this ingredient");
        }

        // Calculate expiry date from shelf life if not provided
        LocalDate expiryDate = request.getExpiryDate();
        if (expiryDate == null && ingredient.getDefaultShelfLifeDays() != null) {
            expiryDate = request.getReceivedDate().plusDays(ingredient.getDefaultShelfLifeDays());
        }

        // Get supplier if provided
        Supplier supplier = null;
        if (request.getSupplierId() != null) {
            supplier = supplierRepository.findById(request.getSupplierId()).orElse(null);
        }

        InventoryBatch batch = InventoryBatch.builder()
                .ingredient(ingredient)
                .batchNumber(batchNumber)
                .quantity(request.getQuantity())
                .initialQuantity(request.getQuantity())
                .receivedDate(request.getReceivedDate())
                .expiryDate(expiryDate)
                .costPerUnit(request.getCostPerUnit() != null ? request.getCostPerUnit() : ingredient.getCostPerUnit())
                .supplier(supplier)
                .poReference(request.getPoReference())
                .notes(request.getNotes())
                .status(InventoryBatch.Status.ACTIVE)
                .build();

        InventoryBatch savedBatch = batchRepository.save(batch);

        // Update ingredient's current stock
        ingredient.addStock(request.getQuantity());
        ingredientRepository.save(ingredient);

        log.info("Batch created: {} for ingredient: {}", batchNumber, ingredient.getName());
        return savedBatch;
    }

    /**
     * Get all batches for an ingredient (FEFO order)
     */
    @Transactional(readOnly = true)
    public List<BatchResponse> getBatchesByIngredient(Long ingredientId) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found"));

        int alertDays = ingredient.getExpiryAlertDays() != null ? ingredient.getExpiryAlertDays() : 7;

        return batchRepository.findByIngredientIdOrderByExpiryDateAsc(ingredientId).stream()
                .map(batch -> BatchResponse.fromEntity(batch, alertDays))
                .collect(Collectors.toList());
    }

    /**
     * Get expiring batches for a restaurant
     */
    @Transactional(readOnly = true)
    public List<BatchResponse> getExpiringBatches(Long restaurantId, int withinDays) {
        LocalDate expiryThreshold = LocalDate.now().plusDays(withinDays);
        List<InventoryBatch> batches = batchRepository.findExpiringBatches(restaurantId, expiryThreshold);

        return batches.stream()
                .map(batch -> {
                    int alertDays = batch.getIngredient().getExpiryAlertDays() != null ?
                            batch.getIngredient().getExpiryAlertDays() : 7;
                    return BatchResponse.fromEntity(batch, alertDays);
                })
                .collect(Collectors.toList());
    }

    /**
     * Get expired batches for a restaurant
     */
    @Transactional(readOnly = true)
    public List<BatchResponse> getExpiredBatches(Long restaurantId) {
        List<InventoryBatch> batches = batchRepository.findExpiredBatches(restaurantId, LocalDate.now());

        return batches.stream()
                .map(batch -> {
                    int alertDays = batch.getIngredient().getExpiryAlertDays() != null ?
                            batch.getIngredient().getExpiryAlertDays() : 7;
                    return BatchResponse.fromEntity(batch, alertDays);
                })
                .collect(Collectors.toList());
    }

    /**
     * Consume stock using FEFO (First Expired First Out)
     * @return List of batches that were consumed from
     */
    @Transactional
    public List<BatchConsumption> consumeStockFEFO(Long ingredientId, BigDecimal quantity, String reason) {
        log.info("Consuming {} from ingredient {} using FEFO", quantity, ingredientId);

        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found"));

        if (!ingredient.getTrackExpiry()) {
            // If not tracking expiry, just deduct from ingredient's stock
            ingredient.deductStock(quantity);
            ingredientRepository.save(ingredient);
            return List.of();
        }

        List<InventoryBatch> activeBatches = batchRepository.findActiveBatchesFEFO(ingredientId);
        List<BatchConsumption> consumptions = new ArrayList<>();

        BigDecimal remaining = quantity;

        for (InventoryBatch batch : activeBatches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal consumed = batch.consume(remaining);
            if (consumed.compareTo(BigDecimal.ZERO) > 0) {
                batchRepository.save(batch);
                consumptions.add(new BatchConsumption(batch.getId(), batch.getBatchNumber(), consumed));
                remaining = remaining.subtract(consumed);

                log.debug("Consumed {} from batch {}", consumed, batch.getBatchNumber());
            }
        }

        // Update ingredient's current stock
        BigDecimal totalConsumed = quantity.subtract(remaining);
        ingredient.deductStock(totalConsumed);
        ingredientRepository.save(ingredient);

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("Could not fully consume requested quantity. Remaining: {}", remaining);
        }

        return consumptions;
    }

    /**
     * Write off a batch (waste, damage, etc.)
     */
    @Transactional
    public void writeOffBatch(Long batchId, String reason) {
        writeOffBatch(batchId, reason, "SYSTEM");
    }

    /**
     * Write off a batch with recorded by information
     */
    @Transactional
    public void writeOffBatch(Long batchId, String reason, String recordedBy) {
        log.info("Writing off batch: {}, reason: {}", batchId, reason);

        InventoryBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        BigDecimal quantity = batch.getQuantity();

        // Determine waste reason based on batch status and reason text
        WasteRecord.WasteReason wasteReason = determineWasteReason(batch, reason);

        // Create waste record before modifying batch
        try {
            wasteService.recordWasteFromBatch(batch, wasteReason, recordedBy, reason);
        } catch (Exception e) {
            log.warn("Failed to create waste record for batch {}: {}", batchId, e.getMessage());
        }

        batch.writeOff(reason);
        batchRepository.save(batch);

        // Deduct from ingredient's stock
        Ingredient ingredient = batch.getIngredient();
        ingredient.deductStock(quantity);
        ingredientRepository.save(ingredient);

        log.info("Batch {} written off, quantity: {}", batch.getBatchNumber(), quantity);
    }

    /**
     * Determine waste reason based on batch status and reason text
     */
    private WasteRecord.WasteReason determineWasteReason(InventoryBatch batch, String reason) {
        if (batch.isExpired() || batch.getStatus() == InventoryBatch.Status.EXPIRED) {
            return WasteRecord.WasteReason.EXPIRED;
        }

        String lowerReason = reason != null ? reason.toLowerCase() : "";
        if (lowerReason.contains("spoil") || lowerReason.contains("rot")) {
            return WasteRecord.WasteReason.SPOILED;
        } else if (lowerReason.contains("damage")) {
            return WasteRecord.WasteReason.DAMAGED;
        } else if (lowerReason.contains("quality")) {
            return WasteRecord.WasteReason.QUALITY_ISSUE;
        } else if (lowerReason.contains("contam")) {
            return WasteRecord.WasteReason.CONTAMINATION;
        }

        return WasteRecord.WasteReason.OTHER;
    }

    /**
     * Update batch expiry date
     */
    @Transactional
    public InventoryBatch updateBatchExpiry(Long batchId, LocalDate newExpiryDate) {
        InventoryBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        batch.setExpiryDate(newExpiryDate);

        // Update status if no longer expired
        if (batch.getStatus() == InventoryBatch.Status.EXPIRED && !batch.isExpired()) {
            batch.setStatus(InventoryBatch.Status.ACTIVE);
        }

        return batchRepository.save(batch);
    }

    /**
     * Mark expired batches as expired (for scheduled job)
     */
    @Transactional
    public int markExpiredBatches(Long restaurantId) {
        List<InventoryBatch> expiredBatches = batchRepository.findExpiredBatches(restaurantId, LocalDate.now());
        int count = 0;

        for (InventoryBatch batch : expiredBatches) {
            if (batch.getStatus() == InventoryBatch.Status.ACTIVE) {
                batch.markExpired();
                batchRepository.save(batch);
                count++;
            }
        }

        if (count > 0) {
            log.info("Marked {} batches as expired for restaurant {}", count, restaurantId);
        }

        return count;
    }

    /**
     * Get expiry summary for a restaurant
     */
    @Transactional(readOnly = true)
    public ExpirySummary getExpirySummary(Long restaurantId, int alertDays) {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(alertDays);

        long expiredCount = batchRepository.countExpiredBatches(restaurantId, today);
        long expiringCount = batchRepository.countExpiringBatches(restaurantId, today, threshold);

        return new ExpirySummary(expiredCount, expiringCount, alertDays);
    }

    /**
     * Get effective stock (excluding expired batches)
     */
    @Transactional(readOnly = true)
    public BigDecimal getEffectiveStock(Long ingredientId) {
        return batchRepository.getEffectiveQuantity(ingredientId, LocalDate.now());
    }

    private String generateBatchNumber(Long ingredientId) {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = batchRepository.countActiveBatches(ingredientId) + 1;
        return String.format("B-%d-%s-%03d", ingredientId, datePrefix, count);
    }

    // Inner classes for return types
    public record BatchConsumption(Long batchId, String batchNumber, BigDecimal quantity) {}

    public record ExpirySummary(long expiredCount, long expiringCount, int alertDays) {}
}

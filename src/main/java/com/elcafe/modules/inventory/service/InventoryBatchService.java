package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.dto.BatchRequest;
import com.elcafe.modules.inventory.dto.BatchResponse;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.entity.Supplier;
import com.elcafe.modules.inventory.entity.WasteRecord;
import com.elcafe.modules.inventory.exception.BatchNotFoundException;
import com.elcafe.modules.inventory.exception.DuplicateBatchNumberException;
import com.elcafe.modules.inventory.exception.IngredientNotFoundException;
import com.elcafe.modules.inventory.repository.InventoryBatchRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryBatchService {

    private final InventoryBatchRepository batchRepository;
    private final InventoryIngredientRepository ingredientRepository;
    private final SupplierRepository supplierRepository;
    private final WasteService wasteService;
    private final StockOperationService stockOperationService;

    // Atomic counter for batch number generation to prevent race conditions
    private static final AtomicLong BATCH_SEQUENCE = new AtomicLong(System.currentTimeMillis() % 100000);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_BATCH_GENERATION_RETRIES = 3;

    /**
     * Create a new batch for an ingredient.
     * Uses REPEATABLE_READ isolation to ensure consistent stock updates
     * when multiple batches are being created concurrently.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public InventoryBatch createBatch(BatchRequest request) {
        log.info("Creating batch for ingredient: {}", request.getIngredientId());

        Ingredient ingredient = ingredientRepository.findById(request.getIngredientId())
                .orElseThrow(() -> new IngredientNotFoundException(request.getIngredientId()));

        // Generate batch number if not provided (uses atomic generation with retry)
        String batchNumber = request.getBatchNumber();
        if (batchNumber == null || batchNumber.isBlank()) {
            batchNumber = generateUniqueBatchNumber(ingredient.getId());
        } else {
            // Check for duplicate batch number when user provides one
            if (batchRepository.findByIngredientIdAndBatchNumber(ingredient.getId(), batchNumber).isPresent()) {
                throw new DuplicateBatchNumberException(ingredient.getId(), batchNumber);
            }
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

        // Update ingredient's current stock using centralized service
        stockOperationService.addStockSimple(ingredient, request.getQuantity());
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
                .orElseThrow(() -> new IngredientNotFoundException(ingredientId));

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
     * Consume stock using FEFO (First Expired First Out).
     * Uses REPEATABLE_READ isolation to prevent phantom reads during batch selection
     * and ensure consistent stock levels during concurrent consumption.
     * @return List of batches that were consumed from
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<BatchConsumption> consumeStockFEFO(Long ingredientId, BigDecimal quantity, String reason) {
        log.info("Consuming {} from ingredient {} using FEFO", quantity, ingredientId);

        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new IngredientNotFoundException(ingredientId));

        if (!ingredient.getTrackExpiry()) {
            // If not tracking expiry, just deduct from ingredient's stock using centralized service
            stockOperationService.deductStockSimple(ingredient, quantity);
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

        // Update ingredient's current stock using centralized service
        BigDecimal totalConsumed = quantity.subtract(remaining);
        stockOperationService.deductStockSimple(ingredient, totalConsumed);
        ingredientRepository.save(ingredient);

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("Could not fully consume requested quantity. Remaining: {}", remaining);
        }

        return consumptions;
    }

    /**
     * Write off a batch (waste, damage, etc.)
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void writeOffBatch(Long batchId, String reason) {
        writeOffBatch(batchId, reason, "SYSTEM");
    }

    /**
     * Write off a batch with recorded by information.
     * Uses REPEATABLE_READ isolation to ensure consistent stock deduction.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void writeOffBatch(Long batchId, String reason, String recordedBy) {
        log.info("Writing off batch: {}, reason: {}", batchId, reason);

        InventoryBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BatchNotFoundException(batchId));

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

        // Deduct from ingredient's stock using centralized service
        Ingredient ingredient = batch.getIngredient();
        stockOperationService.deductStockSimple(ingredient, quantity);
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
                .orElseThrow(() -> new BatchNotFoundException(batchId));

        batch.setExpiryDate(newExpiryDate);

        // Update status if no longer expired
        if (batch.getStatus() == InventoryBatch.Status.EXPIRED && !batch.isExpired()) {
            batch.setStatus(InventoryBatch.Status.ACTIVE);
        }

        return batchRepository.save(batch);
    }

    /**
     * Mark expired batches as expired and deduct from ingredient stock (for scheduled job).
     * This method fixes the phantom stock issue by ensuring currentStock is reduced
     * when batches expire, maintaining consistency between Ingredient.currentStock
     * and InventoryBatch quantities.
     * Uses REPEATABLE_READ isolation to ensure consistent stock deduction during batch processing.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public int markExpiredBatches(Long restaurantId) {
        List<InventoryBatch> expiredBatches = batchRepository.findExpiredBatches(restaurantId, LocalDate.now());
        int count = 0;
        BigDecimal totalExpiredQuantity = BigDecimal.ZERO;

        for (InventoryBatch batch : expiredBatches) {
            if (batch.getStatus() == InventoryBatch.Status.ACTIVE) {
                BigDecimal expiredQuantity = batch.getQuantity();

                // Mark batch as expired
                batch.markExpired();
                batchRepository.save(batch);

                // CRITICAL FIX: Deduct expired quantity from ingredient's currentStock
                // to prevent phantom stock using centralized service
                if (expiredQuantity != null && expiredQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    Ingredient ingredient = batch.getIngredient();
                    stockOperationService.forceDeductStockSimple(ingredient, expiredQuantity);
                    ingredientRepository.save(ingredient);

                    log.info("Deducted {} {} of {} from stock due to batch {} expiry",
                            expiredQuantity, ingredient.getUnit(), ingredient.getName(),
                            batch.getBatchNumber());

                    totalExpiredQuantity = totalExpiredQuantity.add(expiredQuantity);

                    // Optionally create a waste record for the expired stock
                    try {
                        wasteService.recordWasteFromBatch(batch,
                                WasteRecord.WasteReason.EXPIRED,
                                "SYSTEM",
                                "Auto-expired by scheduled job");
                    } catch (Exception e) {
                        log.warn("Failed to create waste record for expired batch {}: {}",
                                batch.getBatchNumber(), e.getMessage());
                    }
                }

                count++;
            }
        }

        if (count > 0) {
            log.info("Marked {} batches as expired for restaurant {}, total quantity removed: {}",
                    count, restaurantId, totalExpiredQuantity);
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

    /**
     * Generate a unique batch number using atomic sequence to prevent race conditions.
     * Format: B-{ingredientId}-{yyyyMMddHHmmss}-{sequence}{random}
     * The combination of timestamp, atomic sequence, and random component ensures uniqueness
     * even under high concurrent load.
     */
    private String generateBatchNumber(Long ingredientId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        long sequence = BATCH_SEQUENCE.incrementAndGet() % 1000;
        int randomSuffix = SECURE_RANDOM.nextInt(100);
        return String.format("B-%d-%s-%03d%02d", ingredientId, timestamp, sequence, randomSuffix);
    }

    /**
     * Generate batch number with retry logic for database-level uniqueness validation.
     * Falls back to regeneration if duplicate is detected.
     */
    private String generateUniqueBatchNumber(Long ingredientId) {
        for (int attempt = 0; attempt < MAX_BATCH_GENERATION_RETRIES; attempt++) {
            String batchNumber = generateBatchNumber(ingredientId);
            if (batchRepository.findByIngredientIdAndBatchNumber(ingredientId, batchNumber).isEmpty()) {
                return batchNumber;
            }
            log.warn("Batch number collision detected: {} (attempt {}), regenerating...",
                    batchNumber, attempt + 1);
        }
        // Last resort: use full timestamp with nanoseconds
        String fallback = String.format("B-%d-%s", ingredientId, System.nanoTime());
        log.info("Using fallback batch number: {}", fallback);
        return fallback;
    }

    // Inner classes for return types
    public record BatchConsumption(Long batchId, String batchNumber, BigDecimal quantity) {}

    public record ExpirySummary(long expiredCount, long expiringCount, int alertDays) {}
}

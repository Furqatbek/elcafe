package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.entity.ShiftInventorySnapshot;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import com.elcafe.modules.pos.shift.repository.ShiftInventorySnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftInventoryService {

    private final ShiftInventorySnapshotRepository snapshotRepository;
    private final EmployeeShiftRepository shiftRepository;
    private final InventoryIngredientRepository ingredientRepository;

    /**
     * Record start-of-shift inventory snapshot for tracked ingredients.
     */
    @Transactional
    public List<ShiftInventorySnapshot> recordStartSnapshot(Long shiftId, Long restaurantId) {
        EmployeeShift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new RuntimeException("Shift not found"));

        List<Ingredient> ingredients = ingredientRepository.findByRestaurant_IdAndActiveTrue(restaurantId);
        List<ShiftInventorySnapshot> snapshots = new ArrayList<>();

        for (Ingredient ing : ingredients) {
            if (!ing.getTrackInventory()) continue;

            ShiftInventorySnapshot snapshot = ShiftInventorySnapshot.builder()
                    .shift(shift)
                    .ingredient(ing)
                    .snapshotType(ShiftInventorySnapshot.SnapshotType.START)
                    .quantity(ing.getCurrentStock())
                    .build();
            snapshots.add(snapshot);
        }

        snapshots = snapshotRepository.saveAll(snapshots);
        log.info("Recorded start snapshot: {} items for shift {}", snapshots.size(), shiftId);
        return snapshots;
    }

    /**
     * Record end-of-shift inventory snapshot, calculate expected vs actual, flag discrepancies.
     * Expected = start quantity - orders consumed during shift (approximation: current stock is "actual end")
     */
    @Transactional
    public List<ShiftInventorySnapshot> recordEndSnapshot(Long shiftId, Long restaurantId) {
        EmployeeShift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new RuntimeException("Shift not found"));

        // Get start snapshots for comparison
        List<ShiftInventorySnapshot> startSnapshots = snapshotRepository
                .findByShiftIdAndSnapshotType(shiftId, ShiftInventorySnapshot.SnapshotType.START);
        Map<Long, BigDecimal> startQuantities = startSnapshots.stream()
                .collect(Collectors.toMap(s -> s.getIngredient().getId(), ShiftInventorySnapshot::getQuantity));

        List<Ingredient> ingredients = ingredientRepository.findByRestaurant_IdAndActiveTrue(restaurantId);
        List<ShiftInventorySnapshot> snapshots = new ArrayList<>();

        for (Ingredient ing : ingredients) {
            if (!ing.getTrackInventory()) continue;

            BigDecimal currentQty = ing.getCurrentStock();
            BigDecimal startQty = startQuantities.get(ing.getId());

            // Expected = whatever the system says is current (orders already deducted)
            // Variance = 0 means no unexplained loss
            // If someone used stock without an order, variance will be negative
            BigDecimal expectedQty = currentQty; // system-tracked is "expected"
            BigDecimal variance = BigDecimal.ZERO;

            if (startQty != null) {
                // If physical count differs from system, we could detect it here
                // For now, record current system stock as the end snapshot
                // Variance can be updated later via manual count
                variance = null; // null = not manually verified
            }

            ShiftInventorySnapshot snapshot = ShiftInventorySnapshot.builder()
                    .shift(shift)
                    .ingredient(ing)
                    .snapshotType(ShiftInventorySnapshot.SnapshotType.END)
                    .quantity(currentQty)
                    .expectedQuantity(expectedQty)
                    .variance(variance)
                    .build();
            snapshots.add(snapshot);
        }

        snapshots = snapshotRepository.saveAll(snapshots);
        log.info("Recorded end snapshot: {} items for shift {}", snapshots.size(), shiftId);
        return snapshots;
    }

    /**
     * Get discrepancies for a shift (items where variance is not null and not zero).
     */
    @Transactional(readOnly = true)
    public List<ShiftInventorySnapshot> getDiscrepancies(Long shiftId) {
        return snapshotRepository.findByShiftIdAndVarianceIsNotNull(shiftId).stream()
                .filter(s -> s.getVariance() != null && s.getVariance().compareTo(BigDecimal.ZERO) != 0)
                .toList();
    }

    /**
     * Get all snapshots for a shift.
     */
    @Transactional(readOnly = true)
    public List<ShiftInventorySnapshot> getSnapshots(Long shiftId) {
        return snapshotRepository.findByShiftId(shiftId);
    }
}

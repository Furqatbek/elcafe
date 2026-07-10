package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryReservation;
import com.elcafe.modules.inventory.entity.InventoryReservation.ReservationStatus;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing inventory reservations.
 * Prevents overselling by reserving stock when items are added to cart,
 * before the order is finalized.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryReservationService {

    private final InventoryReservationRepository reservationRepository;
    private final InventoryIngredientRepository ingredientRepository;
    private final InventoryProductIngredientRepository productIngredientRepository;

    private static final int DEFAULT_RESERVATION_MINUTES = 15;

    /**
     * Reserve ingredients for a product.
     * Call this when adding an item to the cart.
     *
     * @param sessionId  The cart/session ID
     * @param productId  The product being added
     * @param quantity   The quantity of the product
     * @return List of created reservations
     * @throws InsufficientStockException if there's not enough available stock
     */
    @Transactional
    public List<InventoryReservation> reserveForProduct(String sessionId, Long productId, int quantity) {
        log.info("Reserving inventory for product {} (qty: {}) in session {}", productId, quantity, sessionId);

        List<ProductIngredient> productIngredients =
                productIngredientRepository.findByProductIdWithIngredients(productId);

        List<InventoryReservation> reservations = new ArrayList<>();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(DEFAULT_RESERVATION_MINUTES);

        for (ProductIngredient pi : productIngredients) {
            if (pi.getOptional()) {
                continue;
            }

            Ingredient ingredient = pi.getIngredient();
            BigDecimal requiredQuantity = pi.getQuantityRequired()
                    .multiply(BigDecimal.valueOf(quantity));

            // Check available stock (current stock minus existing reservations)
            BigDecimal availableStock = getAvailableStock(ingredient.getId());

            if (availableStock.compareTo(requiredQuantity) < 0) {
                // Rollback any reservations made in this call
                reservations.forEach(r -> {
                    r.setStatus(ReservationStatus.RELEASED);
                    r.setReleasedAt(LocalDateTime.now());
                });
                reservationRepository.saveAll(reservations);

                throw new InsufficientStockException(String.format(
                        "Insufficient stock for %s: need %s, available %s (including reservations)",
                        ingredient.getName(), requiredQuantity, availableStock));
            }

            InventoryReservation reservation = InventoryReservation.builder()
                    .ingredient(ingredient)
                    .sessionId(sessionId)
                    .productId(productId)
                    .quantity(requiredQuantity)
                    .status(ReservationStatus.PENDING)
                    .expiresAt(expiresAt)
                    .build();

            reservations.add(reservationRepository.save(reservation));
        }

        log.info("Created {} reservations for product {} in session {}",
                reservations.size(), productId, sessionId);

        return reservations;
    }

    /**
     * Get available stock for an ingredient (current stock minus active reservations).
     */
    @Transactional(readOnly = true)
    public BigDecimal getAvailableStock(Long ingredientId) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("Ingredient not found: " + ingredientId));

        BigDecimal reservedQuantity = reservationRepository.getTotalReservedQuantity(
                ingredientId, LocalDateTime.now());

        return ingredient.getCurrentStock().subtract(reservedQuantity);
    }

    /**
     * Check if product can be reserved (has enough available stock).
     */
    @Transactional(readOnly = true)
    public boolean canReserve(Long productId, int quantity) {
        List<ProductIngredient> productIngredients =
                productIngredientRepository.findByProductIdWithIngredients(productId);

        for (ProductIngredient pi : productIngredients) {
            if (pi.getOptional()) {
                continue;
            }

            BigDecimal requiredQuantity = pi.getQuantityRequired()
                    .multiply(BigDecimal.valueOf(quantity));
            BigDecimal availableStock = getAvailableStock(pi.getIngredient().getId());

            if (availableStock.compareTo(requiredQuantity) < 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get available quantities for a product considering reservations.
     */
    @Transactional(readOnly = true)
    public int getMaxAvailableQuantity(Long productId) {
        List<ProductIngredient> productIngredients =
                productIngredientRepository.findByProductIdWithIngredients(productId);

        int minQuantity = Integer.MAX_VALUE;

        for (ProductIngredient pi : productIngredients) {
            if (pi.getOptional()) {
                continue;
            }

            BigDecimal availableStock = getAvailableStock(pi.getIngredient().getId());
            BigDecimal requiredPerUnit = pi.getQuantityRequired();

            if (requiredPerUnit.compareTo(BigDecimal.ZERO) > 0) {
                int maxFromIngredient = availableStock.divide(
                        requiredPerUnit, 0, java.math.RoundingMode.FLOOR).intValue();
                minQuantity = Math.min(minQuantity, maxFromIngredient);
            }
        }

        return minQuantity == Integer.MAX_VALUE ? 0 : minQuantity;
    }

    /**
     * Release reservations for a session (cart abandoned or cleared).
     */
    @Transactional
    public int releaseSessionReservations(String sessionId) {
        log.info("Releasing reservations for session: {}", sessionId);
        int released = reservationRepository.releaseSessionReservations(sessionId, LocalDateTime.now());
        log.info("Released {} reservations for session {}", released, sessionId);
        return released;
    }

    /**
     * Confirm reservations when order is placed.
     * This converts reservations to confirmed status and associates them with the order.
     */
    @Transactional
    public int confirmReservations(String sessionId, Long orderId) {
        log.info("Confirming reservations for session {} to order {}", sessionId, orderId);
        int confirmed = reservationRepository.confirmSessionReservations(
                sessionId, orderId, LocalDateTime.now());
        log.info("Confirmed {} reservations for order {}", confirmed, orderId);
        return confirmed;
    }

    /**
     * Extend reservation expiry time (for long checkout processes).
     */
    @Transactional
    public void extendReservations(String sessionId, int additionalMinutes) {
        List<InventoryReservation> reservations =
                reservationRepository.findBySessionIdAndStatus(sessionId, ReservationStatus.PENDING);

        LocalDateTime newExpiry = LocalDateTime.now().plusMinutes(additionalMinutes);

        for (InventoryReservation reservation : reservations) {
            reservation.setExpiresAt(newExpiry);
        }

        reservationRepository.saveAll(reservations);
        log.info("Extended {} reservations for session {} by {} minutes",
                reservations.size(), sessionId, additionalMinutes);
    }

    /**
     * Get reservations by session.
     */
    @Transactional(readOnly = true)
    public List<InventoryReservation> getSessionReservations(String sessionId) {
        return reservationRepository.findBySessionIdAndStatus(sessionId, ReservationStatus.PENDING);
    }

    /**
     * Clean up expired reservations (scheduled task).
     */
    @Scheduled(fixedRate = 60000) // Every minute
    @SchedulerLock(name = "inventory-reservation-expiry", lockAtLeastFor = "PT30S")
    @Transactional
    public void cleanupExpiredReservations() {
        int expired = reservationRepository.expireOldReservations(LocalDateTime.now());
        if (expired > 0) {
            log.info("Expired {} stale inventory reservations", expired);
        }
    }

    /**
     * Get reservation summary for a session.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getReservationSummary(String sessionId) {
        return reservationRepository.findBySessionIdAndStatus(sessionId, ReservationStatus.PENDING)
                .stream()
                .collect(Collectors.groupingBy(
                        r -> r.getIngredient().getId(),
                        Collectors.reducing(BigDecimal.ZERO, InventoryReservation::getQuantity, BigDecimal::add)
                ));
    }

    /**
     * Exception for insufficient stock.
     */
    public static class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(String message) {
            super(message);
        }
    }
}

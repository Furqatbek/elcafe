package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueService {

    private final JournalService journalService;
    private final AccountRepository accountRepository;
    private final InventoryProductIngredientRepository productIngredientRepository;

    /**
     * Record revenue from a completed order
     */
    @Transactional
    public void recordOrderRevenue(Order order) {
        log.info("Recording revenue for order: {}", order.getId());

        try {
            Long restaurantId = order.getRestaurant().getId();

            // Find revenue and cash accounts
            Account salesAccount = accountRepository.findByRestaurantIdAndCategory(
                    restaurantId, Account.AccountCategory.SALES
            ).stream().findFirst().orElse(null);

            Account cashAccount = accountRepository.findByRestaurantIdAndCategory(
                    restaurantId, Account.AccountCategory.CASH
            ).stream().findFirst().orElse(null);

            if (salesAccount != null && cashAccount != null) {
                // Debit: Cash, Credit: Sales Revenue
                journalService.createJournalEntry(
                        restaurantId,
                        LocalDate.now(),
                        "Sales from Order #" + order.getId(),
                        "ORDER",
                        order.getId(),
                        cashAccount.getId(),
                        salesAccount.getId(),
                        order.getTotal(),
                        "SYSTEM"
                );
            }

            // Record delivery fees if applicable
            if (order.getDeliveryFee() != null && order.getDeliveryFee().compareTo(BigDecimal.ZERO) > 0) {
                recordDeliveryFee(order);
            }

            // Record COGS for the order
            recordCogs(order);

        } catch (Exception e) {
            log.error("Failed to record order revenue for order: {}", order.getId(), e);
            // Don't throw - we don't want to fail the order completion
        }
    }

    /**
     * Record delivery fees
     */
    private void recordDeliveryFee(Order order) {
        try {
            Long restaurantId = order.getRestaurant().getId();

            Account deliveryFeeAccount = accountRepository.findByRestaurantIdAndCategory(
                    restaurantId, Account.AccountCategory.DELIVERY_FEES
            ).stream().findFirst().orElse(null);

            Account cashAccount = accountRepository.findByRestaurantIdAndCategory(
                    restaurantId, Account.AccountCategory.CASH
            ).stream().findFirst().orElse(null);

            if (deliveryFeeAccount != null && cashAccount != null) {
                // Debit: Cash, Credit: Delivery Fees Revenue
                journalService.createJournalEntry(
                        restaurantId,
                        LocalDate.now(),
                        "Delivery Fee from Order #" + order.getId(),
                        "DELIVERY_FEE",
                        order.getId(),
                        cashAccount.getId(),
                        deliveryFeeAccount.getId(),
                        order.getDeliveryFee(),
                        "SYSTEM"
                );
            }
        } catch (Exception e) {
            log.warn("Failed to record delivery fee for order: {}", order.getId(), e);
        }
    }

    /**
     * Record COGS (Cost of Goods Sold) for the order
     */
    @Transactional
    public void recordCogs(Order order) {
        log.info("Recording COGS for order: {}", order.getId());

        try {
            Long restaurantId = order.getRestaurant().getId();

            // Find COGS and Inventory accounts
            Account cogsAccount = accountRepository.findByRestaurantIdAndCategory(
                    restaurantId, Account.AccountCategory.COGS
            ).stream().findFirst().orElse(null);

            Account inventoryAccount = accountRepository.findByRestaurantIdAndCategory(
                    restaurantId, Account.AccountCategory.INVENTORY
            ).stream().findFirst().orElse(null);

            if (cogsAccount == null || inventoryAccount == null) {
                log.warn("COGS or Inventory account not found for restaurant: {}", restaurantId);
                return;
            }

            BigDecimal totalCogs = BigDecimal.ZERO;

            // Calculate COGS for each order item
            for (OrderItem item : order.getItems()) {
                BigDecimal itemCogs = calculateItemCogs(item);
                totalCogs = totalCogs.add(itemCogs);
            }

            if (totalCogs.compareTo(BigDecimal.ZERO) > 0) {
                // Debit: COGS, Credit: Inventory
                journalService.createJournalEntry(
                        restaurantId,
                        LocalDate.now(),
                        "COGS for Order #" + order.getId(),
                        "ORDER_COGS",
                        order.getId(),
                        cogsAccount.getId(),
                        inventoryAccount.getId(),
                        totalCogs,
                        "SYSTEM"
                );

                log.info("COGS recorded for order {}: {}", order.getId(), totalCogs);
            }

        } catch (Exception e) {
            log.error("Failed to record COGS for order: {}", order.getId(), e);
        }
    }

    /**
     * Calculate COGS for a single order item based on ingredients
     */
    private BigDecimal calculateItemCogs(OrderItem orderItem) {
        BigDecimal itemCogs = BigDecimal.ZERO;

        try {
            // Get ingredients for this product
            List<ProductIngredient> productIngredients = productIngredientRepository
                    .findByProductIdWithIngredients(orderItem.getProductId());

            for (ProductIngredient pi : productIngredients) {
                if (pi.getOptional()) {
                    continue; // Skip optional ingredients
                }

                Ingredient ingredient = pi.getIngredient();

                // Calculate cost: (quantity required per unit) * (order quantity) * (cost per unit)
                if (ingredient.getCostPerUnit() != null) {
                    BigDecimal quantityUsed = pi.getQuantityRequired()
                            .multiply(new BigDecimal(orderItem.getQuantity()));

                    BigDecimal ingredientCost = quantityUsed.multiply(ingredient.getCostPerUnit());
                    itemCogs = itemCogs.add(ingredientCost);
                }
            }

        } catch (Exception e) {
            log.warn("Failed to calculate COGS for order item: {}", orderItem.getId(), e);
        }

        return itemCogs;
    }

    /**
     * Record a refund for an order
     */
    @Transactional
    public void recordRefund(Order order, BigDecimal refundAmount, String reason) {
        log.info("Recording refund for order: {}, amount: {}", order.getId(), refundAmount);

        try {
            Long restaurantId = order.getRestaurant().getId();

            // Find revenue and cash accounts
            Account salesAccount = accountRepository.findByRestaurantIdAndCategory(
                    restaurantId, Account.AccountCategory.SALES
            ).stream().findFirst().orElse(null);

            Account cashAccount = accountRepository.findByRestaurantIdAndCategory(
                    restaurantId, Account.AccountCategory.CASH
            ).stream().findFirst().orElse(null);

            if (salesAccount != null && cashAccount != null) {
                // Debit: Sales Revenue (reversal), Credit: Cash (refund)
                journalService.createJournalEntry(
                        restaurantId,
                        LocalDate.now(),
                        "Refund for Order #" + order.getId() + ": " + reason,
                        "REFUND",
                        order.getId(),
                        salesAccount.getId(),
                        cashAccount.getId(),
                        refundAmount,
                        "SYSTEM"
                );
            }

        } catch (Exception e) {
            log.error("Failed to record refund for order: {}", order.getId(), e);
        }
    }
}

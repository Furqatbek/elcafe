package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.JournalEntryRepository;
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
    private final JournalEntryRepository journalEntryRepository;
    private final InventoryProductIngredientRepository productIngredientRepository;

    /**
     * Record revenue from a completed order
     */
    @Transactional
    public void recordOrderRevenue(Order order) {
        log.info("Recording revenue for order: {}", order.getId());

        try {
            // Check if revenue has already been recorded for this order
            if (journalEntryRepository.existsByReferenceTypeAndReferenceId("ORDER", order.getId())) {
                log.info("Revenue already recorded for order: {}, skipping", order.getId());
                return;
            }

            Long restaurantId = order.getRestaurant().getId();

            // Use order's completion date or creation date for accurate historical reporting
            LocalDate orderDate = order.getCompletedAt() != null
                    ? order.getCompletedAt().toLocalDate()
                    : (order.getCreatedAt() != null ? order.getCreatedAt().toLocalDate() : LocalDate.now());

            // Find revenue and cash accounts
            Account salesAccount = accountRepository.findByRestaurant_IdAndCategory(
                    restaurantId, Account.AccountCategory.SALES
            ).stream().findFirst().orElse(null);

            Account cashAccount = accountRepository.findByRestaurant_IdAndCategory(
                    restaurantId, Account.AccountCategory.CASH
            ).stream().findFirst().orElse(null);

            if (salesAccount != null && cashAccount != null) {
                // Debit: Cash, Credit: Sales Revenue
                journalService.createJournalEntry(
                        restaurantId,
                        orderDate,
                        "Sales from Order #" + order.getId(),
                        "ORDER",
                        order.getId(),
                        cashAccount.getId(),
                        salesAccount.getId(),
                        order.getTotal(),
                        "SYSTEM"
                );
            } else {
                log.error("Cannot record revenue for order {}: salesAccount={}, cashAccount={}. " +
                        "Please initialize chart of accounts for restaurant {}",
                        order.getId(), salesAccount != null, cashAccount != null, restaurantId);
            }

            // Record service fees if applicable
            if (order.getServiceFee() != null && order.getServiceFee().compareTo(BigDecimal.ZERO) > 0) {
                recordServiceFee(order, orderDate);
            }

            // Record delivery fees if applicable
            if (order.getDeliveryFee() != null && order.getDeliveryFee().compareTo(BigDecimal.ZERO) > 0) {
                recordDeliveryFee(order, orderDate);
            }

            // Record COGS for the order
            recordCogs(order, orderDate);

        } catch (Exception e) {
            log.error("Failed to record order revenue for order: {}", order.getId(), e);
            // Don't throw - we don't want to fail the order completion
        }
    }

    /**
     * Record service fees
     */
    private void recordServiceFee(Order order, LocalDate orderDate) {
        try {
            Long restaurantId = order.getRestaurant().getId();

            Account serviceFeeAccount = accountRepository.findByRestaurant_IdAndCategory(
                    restaurantId, Account.AccountCategory.SERVICE_FEES
            ).stream().findFirst().orElse(null);

            Account cashAccount = accountRepository.findByRestaurant_IdAndCategory(
                    restaurantId, Account.AccountCategory.CASH
            ).stream().findFirst().orElse(null);

            if (serviceFeeAccount != null && cashAccount != null) {
                // Debit: Cash, Credit: Service Fees Revenue
                journalService.createJournalEntry(
                        restaurantId,
                        orderDate,
                        "Service Fee from Order #" + order.getId(),
                        "SERVICE_FEE",
                        order.getId(),
                        cashAccount.getId(),
                        serviceFeeAccount.getId(),
                        order.getServiceFee(),
                        "SYSTEM"
                );
            } else {
                log.warn("Cannot record service fee for order {}: serviceFeeAccount={}, cashAccount={}",
                        order.getId(), serviceFeeAccount != null, cashAccount != null);
            }
        } catch (Exception e) {
            log.error("Failed to record service fee for order {}: {}", order.getId(), e.getMessage(), e);
        }
    }

    /**
     * Record delivery fees
     */
    private void recordDeliveryFee(Order order, LocalDate orderDate) {
        try {
            Long restaurantId = order.getRestaurant().getId();

            Account deliveryFeeAccount = accountRepository.findByRestaurant_IdAndCategory(
                    restaurantId, Account.AccountCategory.DELIVERY_FEES
            ).stream().findFirst().orElse(null);

            Account cashAccount = accountRepository.findByRestaurant_IdAndCategory(
                    restaurantId, Account.AccountCategory.CASH
            ).stream().findFirst().orElse(null);

            if (deliveryFeeAccount != null && cashAccount != null) {
                // Debit: Cash, Credit: Delivery Fees Revenue
                journalService.createJournalEntry(
                        restaurantId,
                        orderDate,
                        "Delivery Fee from Order #" + order.getId(),
                        "DELIVERY_FEE",
                        order.getId(),
                        cashAccount.getId(),
                        deliveryFeeAccount.getId(),
                        order.getDeliveryFee(),
                        "SYSTEM"
                );
            } else {
                log.warn("Cannot record delivery fee for order {}: deliveryFeeAccount={}, cashAccount={}",
                        order.getId(), deliveryFeeAccount != null, cashAccount != null);
            }
        } catch (Exception e) {
            log.error("Failed to record delivery fee for order {}: {}", order.getId(), e.getMessage(), e);
        }
    }

    /**
     * Record COGS (Cost of Goods Sold) for the order
     */
    @Transactional
    public void recordCogs(Order order, LocalDate orderDate) {
        log.info("Recording COGS for order: {}", order.getId());

        try {
            Long restaurantId = order.getRestaurant().getId();

            // Find COGS and Inventory accounts
            Account cogsAccount = accountRepository.findByRestaurant_IdAndCategory(
                    restaurantId, Account.AccountCategory.COGS
            ).stream().findFirst().orElse(null);

            Account inventoryAccount = accountRepository.findByRestaurant_IdAndCategory(
                    restaurantId, Account.AccountCategory.INVENTORY
            ).stream().findFirst().orElse(null);

            if (cogsAccount == null || inventoryAccount == null) {
                log.error("Cannot record COGS for order {}: cogsAccount={}, inventoryAccount={}. " +
                        "Please initialize chart of accounts for restaurant {}",
                        order.getId(), cogsAccount != null, inventoryAccount != null, restaurantId);
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
                        orderDate,
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
            Account salesAccount = accountRepository.findByRestaurant_IdAndCategory(
                    restaurantId, Account.AccountCategory.SALES
            ).stream().findFirst().orElse(null);

            Account cashAccount = accountRepository.findByRestaurant_IdAndCategory(
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

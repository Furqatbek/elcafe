package com.elcafe.modules.loyalty.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.dto.LoyaltyConfigRequest;
import com.elcafe.modules.loyalty.entity.*;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.loyalty.repository.LoyaltyConfigRepository;
import com.elcafe.modules.loyalty.repository.LoyaltyPromotionRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Main service for loyalty program operations
 * Handles bonus accrual, usage, refunds, and retention mechanics
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoyaltyService {

    private final CustomerLoyaltyRepository customerLoyaltyRepository;
    private final LoyaltyConfigRepository loyaltyConfigRepository;
    private final LoyaltyPromotionRepository loyaltyPromotionRepository;
    private final CustomerRepository customerRepository;
    private final RestaurantRepository restaurantRepository;
    private final BonusService bonusService;
    private final TierService tierService;

    /**
     * Get or create customer loyalty record
     */
    @Transactional
    public CustomerLoyalty getOrCreateCustomerLoyalty(Long customerId) {
        Optional<CustomerLoyalty> existing = customerLoyaltyRepository.findByCustomerId(customerId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        // Create new loyalty record with default tier
        CustomerLoyalty loyalty = CustomerLoyalty.builder()
                .customer(customer)
                .restaurantId(customer.getRestaurantId()) // §3.7: loyalty is per-restaurant
                .currentBalance(BigDecimal.ZERO)
                .lifetimeEarned(BigDecimal.ZERO)
                .lifetimeSpent(BigDecimal.ZERO)
                .totalSpent(BigDecimal.ZERO)
                .orderCount(0)
                .firstOrderBonusClaimed(false)
                .build();

        // Assign default tier
        tierService.getDefaultTier().ifPresent(loyalty::setTier);

        loyalty = customerLoyaltyRepository.save(loyalty);
        log.info("Created new loyalty record for customer {}", customerId);

        return loyalty;
    }

    /**
     * Whether completion accrual already ran for this order, keyed by the bonus ledger's
     * idempotency entry. Used by {@code LoyaltyOrderEventListener} as a replay guard: the ledger
     * itself dedupes, but the stats update and milestone visit counters do not.
     */
    @Transactional(readOnly = true)
    public boolean hasProcessedOrderCompletion(Long orderId) {
        return bonusService.transactionExists(orderBonusKey(orderId));
    }

    private static String orderBonusKey(Long orderId) {
        return "order-bonus-" + orderId;
    }

    /**
     * Process bonus accrual when order is completed
     */
    @Transactional
    public void processOrderCompletion(Order order) {
        log.info("Processing loyalty for completed order {}", order.getId());

        CustomerLoyalty loyalty = getOrCreateCustomerLoyalty(order.getCustomer().getId());
        LoyaltyConfig config = getActiveConfig(order.getRestaurant().getId());

        if (config == null || !config.getEnabled()) {
            log.debug("Loyalty system not enabled for restaurant {}", order.getRestaurant().getId());
            return;
        }

        // Check minimum order amount
        if (config.getMinOrderAmountForBonus() != null &&
            order.getTotal().compareTo(config.getMinOrderAmountForBonus()) < 0) {
            log.debug("Order amount {} below minimum {}", order.getTotal(), config.getMinOrderAmountForBonus());
            return;
        }

        // Calculate base bonus
        BigDecimal baseBonus = calculateBaseBonus(order.getTotal(), config);

        // Apply tier multiplier
        BigDecimal tierMultiplier = tierService.getTierMultiplier(loyalty);
        BigDecimal bonusWithTier = baseBonus.multiply(tierMultiplier).setScale(2, RoundingMode.HALF_UP);

        // Apply active promotions
        BigDecimal finalBonus = applyPromotions(bonusWithTier, order, config);

        // Record the transaction
        String idempotencyKey = orderBonusKey(order.getId());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("orderId", order.getId());
        metadata.put("orderAmount", order.getTotal());
        metadata.put("baseBonus", baseBonus);
        metadata.put("tierMultiplier", tierMultiplier);
        metadata.put("restaurantId", order.getRestaurant().getId());

        bonusService.recordTransaction(
                loyalty,
                BonusTransaction.TransactionType.EARNED,
                finalBonus,
                order,
                String.format("Bonus earned from order #%s", order.getOrderNumber()),
                idempotencyKey,
                metadata
        );

        // Update customer stats
        loyalty.setTotalSpent(loyalty.getTotalSpent().add(order.getTotal()));
        loyalty.setOrderCount(loyalty.getOrderCount() + 1);
        loyalty.setLastOrderDate(OffsetDateTime.now(ZoneOffset.UTC));

        // Check for first order bonus
        if (!loyalty.getFirstOrderBonusClaimed() && config.getFirstOrderBonusAmount().compareTo(BigDecimal.ZERO) > 0) {
            grantFirstOrderBonus(loyalty, config, order);
        }

        // Check and upgrade tier
        boolean tierUpgraded = tierService.checkAndUpgradeTier(loyalty);
        if (tierUpgraded) {
            log.info("Customer {} upgraded to tier {}", loyalty.getCustomer().getId(), loyalty.getTier().getName());
        }

        customerLoyaltyRepository.save(loyalty);
    }

    /**
     * Use bonus for order payment
     */
    @Transactional
    public BigDecimal useBonusForPayment(Long customerId, BigDecimal orderAmount, BigDecimal requestedBonusAmount, Order order) {
        log.info("Processing bonus usage for customer {}: requested={}", customerId, requestedBonusAmount);

        CustomerLoyalty loyalty = getOrCreateCustomerLoyalty(customerId);
        LoyaltyConfig config = getActiveConfig(order.getRestaurant().getId());

        if (config == null || !config.getEnabled()) {
            throw new BadRequestException("Loyalty system not enabled");
        }

        // Calculate maximum allowed bonus usage
        BigDecimal maxBonusAmount = orderAmount
                .multiply(BigDecimal.valueOf(config.getMaxBonusPaymentPercentage()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // Determine actual bonus to use
        BigDecimal actualBonusAmount = requestedBonusAmount
                .min(maxBonusAmount)
                .min(loyalty.getCurrentBalance());

        if (actualBonusAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("No bonus will be used for this order");
            return BigDecimal.ZERO;
        }

        // Check sufficient balance
        if (!loyalty.hasSufficientBalance(actualBonusAmount)) {
            throw new BadRequestException("Insufficient bonus balance");
        }

        // Record the transaction
        String idempotencyKey = "order-payment-" + order.getId();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("orderId", order.getId());
        metadata.put("orderAmount", orderAmount);
        metadata.put("requestedAmount", requestedBonusAmount);
        metadata.put("maxAllowed", maxBonusAmount);

        bonusService.recordTransaction(
                loyalty,
                BonusTransaction.TransactionType.SPENT,
                actualBonusAmount,
                order,
                String.format("Bonus used for order #%s", order.getOrderNumber()),
                idempotencyKey,
                metadata
        );

        customerLoyaltyRepository.save(loyalty);

        log.info("Bonus usage processed: customer={}, amount={}", customerId, actualBonusAmount);
        return actualBonusAmount;
    }

    /**
     * Handle refund - rollback bonuses
     */
    @Transactional
    public void processRefund(Order order, BigDecimal refundAmount) {
        log.info("Processing loyalty refund for order {}: amount={}", order.getId(), refundAmount);

        CustomerLoyalty loyalty = customerLoyaltyRepository.findByCustomerId(order.getCustomer().getId())
                .orElse(null);

        if (loyalty == null) {
            log.debug("No loyalty record found for customer {}", order.getCustomer().getId());
            return;
        }

        // Find all transactions for this order
        List<BonusTransaction> orderTransactions = bonusService.getTransactionsByOrder(order.getId());

        for (BonusTransaction transaction : orderTransactions) {
            if (transaction.getTransactionType() == BonusTransaction.TransactionType.EARNED) {
                // Deduct previously earned bonuses
                BigDecimal amountToDeduct = calculateProportionalBonus(transaction.getAmount(), refundAmount, order.getTotal());

                String idempotencyKey = "refund-deduct-" + order.getId() + "-" + transaction.getId();
                bonusService.recordTransaction(
                        loyalty,
                        BonusTransaction.TransactionType.SPENT,
                        amountToDeduct,
                        order,
                        String.format("Bonus deducted due to refund for order #%s", order.getOrderNumber()),
                        idempotencyKey,
                        Map.of("originalTransactionId", transaction.getId(), "refundAmount", refundAmount)
                );

            } else if (transaction.getTransactionType() == BonusTransaction.TransactionType.SPENT) {
                // Refund previously spent bonuses
                BigDecimal amountToRefund = calculateProportionalBonus(transaction.getAmount(), refundAmount, order.getTotal());

                String idempotencyKey = "refund-return-" + order.getId() + "-" + transaction.getId();
                bonusService.recordTransaction(
                        loyalty,
                        BonusTransaction.TransactionType.REFUNDED,
                        amountToRefund,
                        order,
                        String.format("Bonus refunded for order #%s", order.getOrderNumber()),
                        idempotencyKey,
                        Map.of("originalTransactionId", transaction.getId(), "refundAmount", refundAmount)
                );
            }
        }

        // Update total spent (partial refund support)
        BigDecimal newTotalSpent = loyalty.getTotalSpent().subtract(refundAmount);
        if (newTotalSpent.compareTo(BigDecimal.ZERO) < 0) {
            newTotalSpent = BigDecimal.ZERO;
        }
        loyalty.setTotalSpent(newTotalSpent);

        // Re-evaluate tier after refund
        tierService.checkAndUpgradeTier(loyalty);

        customerLoyaltyRepository.save(loyalty);
    }

    /**
     * Grant first order bonus
     */
    @Transactional
    protected void grantFirstOrderBonus(CustomerLoyalty loyalty, LoyaltyConfig config, Order order) {
        String idempotencyKey = "first-order-" + loyalty.getCustomer().getId();

        bonusService.recordTransaction(
                loyalty,
                BonusTransaction.TransactionType.FIRST_ORDER_BONUS,
                config.getFirstOrderBonusAmount(),
                order,
                "First order bonus",
                idempotencyKey,
                Map.of("customerId", loyalty.getCustomer().getId())
        );

        loyalty.setFirstOrderBonusClaimed(true);
        log.info("First order bonus granted to customer {}", loyalty.getCustomer().getId());
    }

    /**
     * Grant birthday bonus
     */
    @Transactional
    public void grantBirthdayBonus(Long customerId) {
        CustomerLoyalty loyalty = getOrCreateCustomerLoyalty(customerId);
        LoyaltyConfig config = getActiveConfig(null); // Use global config

        if (config == null || config.getBirthdayBonusAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        int currentYear = LocalDate.now().getYear();
        if (loyalty.getBirthdayBonusClaimedYear() != null &&
            loyalty.getBirthdayBonusClaimedYear() >= currentYear) {
            log.debug("Birthday bonus already claimed for customer {} in year {}", customerId, currentYear);
            return;
        }

        String idempotencyKey = "birthday-" + customerId + "-" + currentYear;
        bonusService.recordTransaction(
                loyalty,
                BonusTransaction.TransactionType.BIRTHDAY_BONUS,
                config.getBirthdayBonusAmount(),
                null,
                "Happy Birthday bonus!",
                idempotencyKey,
                Map.of("year", currentYear)
        );

        loyalty.setBirthdayBonusClaimedYear(currentYear);
        customerLoyaltyRepository.save(loyalty);

        log.info("Birthday bonus granted to customer {}", customerId);
    }

    /**
     * Grant the one-time welcome bonus for completing registration (V182).
     *
     * <p>Called from both registration doors — the online QR menu (after the consumer OTP is accepted,
     * in {@code ConsumerAuthService#verifyOtp}) and the staff-assisted flow at the till. Callers do not
     * need to know whether the customer is new: the {@code registration-<customerId>} idempotency key
     * means the credit happens exactly once per customer forever, so the safe thing is to call this on
     * every successful registration and let the key decide. That is also what makes an abandoned
     * signup free — a guest who requests an OTP and never enters it never reaches this method.
     *
     * @return the amount credited, or {@link BigDecimal#ZERO} when the bonus is off, unconfigured, or
     *         this customer has already had it. Returning the amount (rather than void, as the other
     *         grants do) lets the caller tell the guest what they just earned.
     */
    @Transactional
    public BigDecimal grantRegistrationBonus(Long customerId, Long restaurantId) {
        LoyaltyConfig config = getActiveConfig(restaurantId);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return BigDecimal.ZERO;
        }
        if (!Boolean.TRUE.equals(config.getRegistrationBonusEnabled())) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = config.getRegistrationBonusAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // One per customer, forever — not per year like the birthday grant. recordTransaction is
        // idempotent on this key anyway; the pre-check exists so the caller can be told nothing new was
        // credited, rather than announcing a second welcome bonus that never happened.
        String idempotencyKey = "registration-" + customerId;
        if (bonusService.transactionExists(idempotencyKey)) {
            log.debug("Registration bonus already granted to customer {}", customerId);
            return BigDecimal.ZERO;
        }

        CustomerLoyalty loyalty = getOrCreateCustomerLoyalty(customerId);
        bonusService.recordTransaction(
                loyalty,
                BonusTransaction.TransactionType.REGISTRATION_BONUS,
                amount,
                null,
                "Welcome bonus for registering",
                idempotencyKey,
                Map.of("restaurantId", restaurantId == null ? "" : restaurantId)
        );

        log.info("Registration bonus of {} granted to customer {}", amount, customerId);
        return amount;
    }

    /**
     * Grant reactivation bonus for inactive customers
     */
    @Transactional
    public void grantReactivationBonus(Long customerId) {
        CustomerLoyalty loyalty = getOrCreateCustomerLoyalty(customerId);
        LoyaltyConfig config = getActiveConfig(null);

        if (config == null || config.getReactivationBonusAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // Check if customer was inactive
        if (loyalty.getLastOrderDate() == null) {
            return;
        }

        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minusDays(config.getReactivationDaysThreshold());
        if (loyalty.getLastOrderDate().isAfter(threshold)) {
            return;
        }

        String idempotencyKey = "reactivation-" + customerId + "-" + LocalDate.now();
        bonusService.recordTransaction(
                loyalty,
                BonusTransaction.TransactionType.REACTIVATION_BONUS,
                config.getReactivationBonusAmount(),
                null,
                "Welcome back bonus!",
                idempotencyKey,
                Map.of("lastOrderDate", loyalty.getLastOrderDate())
        );

        customerLoyaltyRepository.save(loyalty);
        log.info("Reactivation bonus granted to customer {}", customerId);
    }

    /**
     * Expire a customer's outstanding bonus balance after a period of inactivity (rolling-inactivity
     * expiry, driven by {@link com.elcafe.modules.loyalty.scheduler.LoyaltyBonusScheduler}). Records an
     * EXPIRED ledger entry for the full balance and zeroes it. Idempotent per customer/day via the
     * ledger idempotency key, so a same-day re-run is a no-op. Returns whether anything was expired.
     */
    @Transactional
    public boolean expireStaleBalance(Long customerLoyaltyId, int inactivityDays) {
        CustomerLoyalty loyalty = customerLoyaltyRepository.findById(customerLoyaltyId).orElse(null);
        if (loyalty == null) {
            return false;
        }
        BigDecimal balance = loyalty.getCurrentBalance();
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        String idempotencyKey = "expiry-" + customerLoyaltyId + "-" + LocalDate.now();
        bonusService.recordTransaction(
                loyalty,
                BonusTransaction.TransactionType.EXPIRED,
                balance,
                null,
                "Bonus expired after " + inactivityDays + " days of inactivity",
                idempotencyKey,
                Map.of("inactivityDays", inactivityDays, "expiredAmount", balance)
        );
        customerLoyaltyRepository.save(loyalty);
        log.info("Expired {} bonus for customer loyalty {} ({}d inactivity)", balance, customerLoyaltyId, inactivityDays);
        return true;
    }

    /**
     * Calculate base bonus amount
     */
    private BigDecimal calculateBaseBonus(BigDecimal orderAmount, LoyaltyConfig config) {
        if (config.getBonusRateType() == LoyaltyConfig.BonusRateType.PERCENTAGE) {
            return orderAmount.multiply(config.getBonusRateValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            return config.getBonusRateValue();
        }
    }

    /**
     * Apply active promotions to bonus amount
     */
    private BigDecimal applyPromotions(BigDecimal baseBonus, Order order, LoyaltyConfig config) {
        List<LoyaltyPromotion> promotions = loyaltyPromotionRepository.findActivePromotions(
                order.getRestaurant().getId(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        BigDecimal finalBonus = baseBonus;

        for (LoyaltyPromotion promotion : promotions) {
            if (!promotion.isCurrentlyActive() || !promotion.isOrderQualified(order.getTotal())) {
                continue;
            }

            if (promotion.getPromotionType() == LoyaltyPromotion.PromotionType.BONUS_MULTIPLIER) {
                finalBonus = finalBonus.multiply(promotion.getMultiplierValue()).setScale(2, RoundingMode.HALF_UP);
            } else if (promotion.getPromotionType() == LoyaltyPromotion.PromotionType.FIXED_BONUS) {
                finalBonus = finalBonus.add(promotion.getFixedBonusAmount());
            } else if (promotion.getPromotionType() == LoyaltyPromotion.PromotionType.PERCENTAGE_BOOST) {
                BigDecimal boost = baseBonus.multiply(promotion.getMultiplierValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                finalBonus = finalBonus.add(boost);
            }

            // Apply max bonus limit if specified
            if (promotion.getMaxBonusPerOrder() != null) {
                finalBonus = finalBonus.min(promotion.getMaxBonusPerOrder());
            }
        }

        return finalBonus;
    }

    /**
     * Calculate proportional bonus for partial refunds
     */
    private BigDecimal calculateProportionalBonus(BigDecimal originalBonus, BigDecimal refundAmount, BigDecimal totalAmount) {
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return originalBonus.multiply(refundAmount)
                .divide(totalAmount, 2, RoundingMode.HALF_UP);
    }

    /**
     * Get active loyalty configuration
     */
    private LoyaltyConfig getActiveConfig(Long restaurantId) {
        if (restaurantId != null) {
            return loyaltyConfigRepository.findActiveConfigForRestaurant(restaurantId).orElse(null);
        }
        return loyaltyConfigRepository.findGlobalConfig().orElse(null);
    }

    /**
     * Get customer loyalty by customer ID
     */
    @Transactional(readOnly = true)
    public Optional<CustomerLoyalty> getCustomerLoyalty(Long customerId) {
        return customerLoyaltyRepository.findByCustomerId(customerId);
    }

    /**
     * Public read of the loyalty config for a restaurant. Falls back to the
     * global config when the restaurant has no override. Returns null if
     * nothing is configured yet.
     */
    @Transactional(readOnly = true)
    public LoyaltyConfig getConfig(Long restaurantId) {
        if (restaurantId != null) {
            Optional<LoyaltyConfig> perRestaurant = loyaltyConfigRepository.findByRestaurant_IdAndEnabled(restaurantId, true);
            if (perRestaurant.isPresent()) return perRestaurant.get();
        }
        return loyaltyConfigRepository.findGlobalConfig().orElse(null);
    }

    /**
     * Upsert the loyalty config for a restaurant (or the global config if
     * restaurantId is null). Only supplied fields overwrite existing values.
     */
    @Transactional
    public LoyaltyConfig upsertConfig(LoyaltyConfigRequest request) {
        // A config with no restaurant cannot work. LoyaltyConfig carries @Filter(restaurant_id =
        // :restaurantId), and tenant enforcement is on by default, so Hibernate ANDs that condition
        // onto every query a tenant makes — and `restaurant_id = 4` never matches NULL. The row is
        // therefore invisible to the restaurant it was meant to configure, invisible to
        // findGlobalConfig() on the next save (so a fresh orphan is written each time), and the UI
        // reports "saved" throughout. Loyalty stays silently off while the operator believes they
        // configured it.
        //
        // The global config is a single-tenant leftover: one set of bonus rates spending every
        // restaurant's money. Rather than resurrect it by weakening the filter on a table that decides
        // payouts, the write is refused and the caller is told to pick a restaurant.
        if (request.getRestaurantId() == null) {
            throw new BadRequestException(
                    "Choose a restaurant. Loyalty settings are per-restaurant — a config saved without "
                            + "one is not applied to anybody: tenant scoping hides it from every "
                            + "restaurant, including the one you are configuring.");
        }

        LoyaltyConfig config = loyaltyConfigRepository
                .findByRestaurant_IdAndEnabled(request.getRestaurantId(), true)
                .orElseGet(() -> {
                    Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                            .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", request.getRestaurantId()));
                    return LoyaltyConfig.builder().restaurant(restaurant).build();
                });

        if (request.getBonusRateType() != null) config.setBonusRateType(request.getBonusRateType());
        if (request.getBonusRateValue() != null) config.setBonusRateValue(request.getBonusRateValue());
        if (request.getMaxBonusPaymentPercentage() != null) config.setMaxBonusPaymentPercentage(request.getMaxBonusPaymentPercentage());
        if (request.getMinOrderAmountForBonus() != null) config.setMinOrderAmountForBonus(request.getMinOrderAmountForBonus());
        if (request.getBirthdayBonusAmount() != null) config.setBirthdayBonusAmount(request.getBirthdayBonusAmount());
        if (request.getFirstOrderBonusAmount() != null) config.setFirstOrderBonusAmount(request.getFirstOrderBonusAmount());
        if (request.getReactivationBonusAmount() != null) config.setReactivationBonusAmount(request.getReactivationBonusAmount());
        if (request.getReactivationDaysThreshold() != null) config.setReactivationDaysThreshold(request.getReactivationDaysThreshold());
        if (request.getBonusExpiryDays() != null) config.setBonusExpiryDays(request.getBonusExpiryDays());
        if (request.getRegistrationBonusAmount() != null) config.setRegistrationBonusAmount(request.getRegistrationBonusAmount());
        if (request.getRegistrationBonusEnabled() != null) config.setRegistrationBonusEnabled(request.getRegistrationBonusEnabled());
        if (request.getEnabled() != null) config.setEnabled(request.getEnabled());

        LoyaltyConfig saved = loyaltyConfigRepository.save(config);
        log.info("Upserted loyalty config id={} restaurantId={} enabled={}",
                saved.getId(),
                saved.getRestaurant() != null ? saved.getRestaurant().getId() : null,
                saved.getEnabled());
        return saved;
    }
}

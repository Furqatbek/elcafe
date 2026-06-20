package com.elcafe.modules.loyalty.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.loyalty.dto.TierRequest;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.entity.CustomerTier;
import com.elcafe.modules.loyalty.entity.TierHistory;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.loyalty.repository.CustomerTierRepository;
import com.elcafe.modules.loyalty.repository.TierHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Service for managing customer tiers and tier upgrades
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TierService {

    private final CustomerTierRepository customerTierRepository;
    private final TierHistoryRepository tierHistoryRepository;
    private final CustomerLoyaltyRepository customerLoyaltyRepository;

    /**
     * Check and update customer tier based on spend and order count
     */
    @Transactional
    public boolean checkAndUpgradeTier(CustomerLoyalty customerLoyalty) {
        log.debug("Checking tier upgrade for customer loyalty {}", customerLoyalty.getId());

        Optional<CustomerTier> highestQualifiedTier = customerTierRepository.findHighestQualifiedTier(
                customerLoyalty.getTotalSpent(),
                customerLoyalty.getOrderCount()
        );

        if (highestQualifiedTier.isEmpty()) {
            log.debug("No qualified tier found for customer {}", customerLoyalty.getId());
            return false;
        }

        CustomerTier newTier = highestQualifiedTier.get();
        CustomerTier currentTier = customerLoyalty.getTier();

        // Check if upgrade is needed
        if (currentTier == null || newTier.getLevel() > currentTier.getLevel()) {
            log.info("Upgrading customer {} from tier {} to tier {}",
                    customerLoyalty.getId(),
                    currentTier != null ? currentTier.getName() : "None",
                    newTier.getName());

            // Record tier history
            TierHistory history = TierHistory.builder()
                    .customerLoyalty(customerLoyalty)
                    .restaurantId(customerLoyalty.getRestaurantId()) // §3.7: inherit the loyalty's tenant
                    .fromTier(currentTier)
                    .toTier(newTier)
                    .reason(String.format("Automatic upgrade - Total spent: %s, Order count: %d",
                            customerLoyalty.getTotalSpent(), customerLoyalty.getOrderCount()))
                    .build();

            tierHistoryRepository.save(history);

            // Update customer loyalty tier
            customerLoyalty.setTier(newTier);

            return true;
        }

        return false;
    }

    /**
     * Get tier multiplier for bonus calculation
     */
    @Transactional(readOnly = true)
    public BigDecimal getTierMultiplier(CustomerLoyalty customerLoyalty) {
        if (customerLoyalty.getTier() == null) {
            return BigDecimal.ONE;
        }
        return customerLoyalty.getTier().getBonusMultiplier();
    }

    /**
     * Get default tier (lowest level)
     */
    @Transactional(readOnly = true)
    public Optional<CustomerTier> getDefaultTier() {
        return customerTierRepository.findByLevel(1);
    }

    /**
     * Get tier by name
     */
    @Transactional(readOnly = true)
    public Optional<CustomerTier> getTierByName(String name) {
        return customerTierRepository.findByName(name);
    }

    @Transactional(readOnly = true)
    public long countCustomersOnTier(Long tierId) {
        Long count = customerLoyaltyRepository.countByTierId(tierId);
        return count == null ? 0L : count;
    }

    @Transactional
    public CustomerTier createTier(TierRequest request) {
        CustomerTier tier = CustomerTier.builder()
                .name(request.getName())
                .level(request.getLevel())
                .minTotalSpend(request.getMinTotalSpend() != null ? request.getMinTotalSpend() : BigDecimal.ZERO)
                .minOrderCount(request.getMinOrderCount() != null ? request.getMinOrderCount() : 0)
                .bonusMultiplier(request.getBonusMultiplier() != null ? request.getBonusMultiplier() : BigDecimal.ONE)
                .benefitsDescription(request.getBenefitsDescription())
                .color(request.getColor())
                .icon(request.getIcon())
                .build();
        CustomerTier saved = customerTierRepository.save(tier);
        log.info("Created loyalty tier {} (level {})", saved.getName(), saved.getLevel());
        return saved;
    }

    @Transactional
    public CustomerTier updateTier(Long id, TierRequest request) {
        CustomerTier tier = customerTierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerTier", "id", id));

        tier.setName(request.getName());
        tier.setLevel(request.getLevel());
        if (request.getMinTotalSpend() != null) tier.setMinTotalSpend(request.getMinTotalSpend());
        if (request.getMinOrderCount() != null) tier.setMinOrderCount(request.getMinOrderCount());
        if (request.getBonusMultiplier() != null) tier.setBonusMultiplier(request.getBonusMultiplier());
        tier.setBenefitsDescription(request.getBenefitsDescription());
        tier.setColor(request.getColor());
        tier.setIcon(request.getIcon());

        CustomerTier saved = customerTierRepository.save(tier);
        log.info("Updated loyalty tier {} (level {})", saved.getName(), saved.getLevel());
        return saved;
    }

    @Transactional
    public void deleteTier(Long id) {
        CustomerTier tier = customerTierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerTier", "id", id));
        long assigned = countCustomersOnTier(id);
        if (assigned > 0) {
            throw new IllegalStateException(
                    "Cannot delete tier '" + tier.getName() + "' — " + assigned + " customer(s) are currently on it. Reassign them first.");
        }
        customerTierRepository.delete(tier);
        log.info("Deleted loyalty tier {} (level {})", tier.getName(), tier.getLevel());
    }
}

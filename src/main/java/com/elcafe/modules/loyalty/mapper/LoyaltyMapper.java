package com.elcafe.modules.loyalty.mapper;

import com.elcafe.modules.loyalty.dto.BonusTransactionResponse;
import com.elcafe.modules.loyalty.dto.CustomerLoyaltyResponse;
import com.elcafe.modules.loyalty.entity.BonusTransaction;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.entity.CustomerTier;
import org.springframework.stereotype.Component;

@Component
public class LoyaltyMapper {

    public CustomerLoyaltyResponse toResponse(CustomerLoyalty loyalty) {
        if (loyalty == null) {
            return null;
        }

        return CustomerLoyaltyResponse.builder()
                .id(loyalty.getId())
                .customerId(loyalty.getCustomer().getId())
                .customerName(loyalty.getCustomer().getFirstName() + " " + loyalty.getCustomer().getLastName())
                .customerEmail(loyalty.getCustomer().getEmail())
                .currentBalance(loyalty.getCurrentBalance())
                .lifetimeEarned(loyalty.getLifetimeEarned())
                .lifetimeSpent(loyalty.getLifetimeSpent())
                .tier(toTierInfo(loyalty.getTier()))
                .totalSpent(loyalty.getTotalSpent())
                .orderCount(loyalty.getOrderCount())
                .lastOrderDate(loyalty.getLastOrderDate())
                .firstOrderBonusClaimed(loyalty.getFirstOrderBonusClaimed())
                .birthdayBonusClaimedYear(loyalty.getBirthdayBonusClaimedYear())
                .createdAt(loyalty.getCreatedAt())
                .updatedAt(loyalty.getUpdatedAt())
                .build();
    }

    public CustomerLoyaltyResponse.TierInfo toTierInfo(CustomerTier tier) {
        return toTierInfo(tier, null);
    }

    public CustomerLoyaltyResponse.TierInfo toTierInfo(CustomerTier tier, Long customerCount) {
        if (tier == null) {
            return null;
        }

        return CustomerLoyaltyResponse.TierInfo.builder()
                .id(tier.getId())
                .name(tier.getName())
                .level(tier.getLevel())
                .bonusMultiplier(tier.getBonusMultiplier())
                .color(tier.getColor())
                .icon(tier.getIcon())
                .benefitsDescription(tier.getBenefitsDescription())
                .minTotalSpend(tier.getMinTotalSpend())
                .minOrderCount(tier.getMinOrderCount())
                .customerCount(customerCount)
                .build();
    }

    public BonusTransactionResponse toResponse(BonusTransaction transaction) {
        if (transaction == null) {
            return null;
        }

        return BonusTransactionResponse.builder()
                .id(transaction.getId())
                .transactionType(transaction.getTransactionType())
                .amount(transaction.getAmount())
                .balanceAfter(transaction.getBalanceAfter())
                .orderId(transaction.getOrder() != null ? transaction.getOrder().getId() : null)
                .orderNumber(transaction.getOrder() != null ? transaction.getOrder().getOrderNumber() : null)
                .description(transaction.getDescription())
                .metadata(transaction.getMetadata())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}

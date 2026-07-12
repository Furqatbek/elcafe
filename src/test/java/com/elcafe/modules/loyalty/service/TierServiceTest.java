package com.elcafe.modules.loyalty.service;

import com.elcafe.exception.BadRequestException;

import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.entity.CustomerTier;
import com.elcafe.modules.loyalty.entity.TierHistory;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.loyalty.repository.CustomerTierRepository;
import com.elcafe.modules.loyalty.repository.TierHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TierServiceTest {

    @Mock private CustomerTierRepository customerTierRepository;
    @Mock private TierHistoryRepository tierHistoryRepository;
    @Mock private CustomerLoyaltyRepository customerLoyaltyRepository;

    @InjectMocks private TierService tierService;

    @Test
    @DisplayName("deleteTier refuses when customers are still assigned")
    void deleteTier_blockedWhenAssigned() {
        CustomerTier tier = CustomerTier.builder().id(2L).name("Bronze").level(2).build();
        when(customerTierRepository.findById(2L)).thenReturn(Optional.of(tier));
        when(customerLoyaltyRepository.countByTierId(2L)).thenReturn(3L);

        assertThatThrownBy(() -> tierService.deleteTier(2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("3");

        verify(customerTierRepository, never()).delete(tier);
    }

    @Test
    @DisplayName("deleteTier removes tier when no customers assigned")
    void deleteTier_ok() {
        CustomerTier tier = CustomerTier.builder().id(2L).name("Bronze").level(2).build();
        when(customerTierRepository.findById(2L)).thenReturn(Optional.of(tier));
        when(customerLoyaltyRepository.countByTierId(2L)).thenReturn(0L);

        tierService.deleteTier(2L);

        verify(customerTierRepository).delete(tier);
    }

    @Test
    @DisplayName("checkAndUpgradeTier promotes when both thresholds met and records history")
    void checkAndUpgradeTier_promotes() {
        CustomerTier silver = CustomerTier.builder().id(1L).name("Silver").level(1).build();
        CustomerTier gold = CustomerTier.builder().id(2L).name("Gold").level(2)
                .minTotalSpend(BigDecimal.valueOf(50000)).minOrderCount(10)
                .bonusMultiplier(new BigDecimal("1.5")).build();
        CustomerLoyalty loyalty = CustomerLoyalty.builder()
                .id(99L).tier(silver)
                .totalSpent(BigDecimal.valueOf(60000)).orderCount(12).build();

        when(customerTierRepository.findHighestQualifiedTier(loyalty.getTotalSpent(), loyalty.getOrderCount()))
                .thenReturn(Optional.of(gold));

        boolean upgraded = tierService.checkAndUpgradeTier(loyalty);

        assertThat(upgraded).isTrue();
        assertThat(loyalty.getTier()).isEqualTo(gold);

        ArgumentCaptor<TierHistory> historyCaptor = ArgumentCaptor.forClass(TierHistory.class);
        verify(tierHistoryRepository).save(historyCaptor.capture());
        TierHistory saved = historyCaptor.getValue();
        assertThat(saved.getFromTier()).isEqualTo(silver);
        assertThat(saved.getToTier()).isEqualTo(gold);
        assertThat(saved.getCustomerLoyalty()).isEqualTo(loyalty);
    }

    @Test
    @DisplayName("checkAndUpgradeTier does not promote when only one threshold met")
    void checkAndUpgradeTier_doesNotPromoteWhenPartial() {
        CustomerTier silver = CustomerTier.builder().id(1L).name("Silver").level(1).build();
        CustomerLoyalty loyalty = CustomerLoyalty.builder()
                .id(99L).tier(silver)
                .totalSpent(BigDecimal.valueOf(60000))  // spend OK
                .orderCount(2)                          // order count NOT OK
                .build();

        // findHighestQualifiedTier filters by both criteria — so when only one
        // is met it returns the current (or a lower) tier. Simulate "no upgrade
        // available" by returning the current tier.
        when(customerTierRepository.findHighestQualifiedTier(any(), any()))
                .thenReturn(Optional.of(silver));

        boolean upgraded = tierService.checkAndUpgradeTier(loyalty);

        assertThat(upgraded).isFalse();
        assertThat(loyalty.getTier()).isEqualTo(silver);
        verify(tierHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("checkAndUpgradeTier no-op when no qualifying tier")
    void checkAndUpgradeTier_noQualifyingTier() {
        CustomerLoyalty loyalty = CustomerLoyalty.builder()
                .id(99L).totalSpent(BigDecimal.ZERO).orderCount(0).build();
        when(customerTierRepository.findHighestQualifiedTier(any(), any()))
                .thenReturn(Optional.empty());

        assertThat(tierService.checkAndUpgradeTier(loyalty)).isFalse();
        verify(tierHistoryRepository, never()).save(any());
    }
}

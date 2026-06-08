package com.elcafe.modules.loyalty.service;

import com.elcafe.modules.loyalty.entity.CustomerTier;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.loyalty.repository.CustomerTierRepository;
import com.elcafe.modules.loyalty.repository.TierHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
                .isInstanceOf(IllegalStateException.class)
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
}

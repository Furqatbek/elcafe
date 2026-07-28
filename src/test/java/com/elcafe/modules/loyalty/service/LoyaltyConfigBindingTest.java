package com.elcafe.modules.loyalty.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.loyalty.dto.LoyaltyConfigRequest;
import com.elcafe.modules.loyalty.entity.LoyaltyConfig;
import com.elcafe.modules.loyalty.repository.LoyaltyConfigRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The same defect as the unbound user account, in the table that decides payouts.
 *
 * <p>{@code LoyaltyConfig} carries {@code @Filter(restaurant_id = :restaurantId)} and tenant
 * enforcement is on by default, so Hibernate ANDs that condition onto every query a restaurant makes —
 * and {@code restaurant_id = 4} never matches NULL. A config saved with no restaurant is therefore:
 *
 * <ul>
 *   <li>invisible to the restaurant it was meant to configure,</li>
 *   <li>invisible to {@code findGlobalConfig()} on the next save, so each save wrote a <em>fresh</em>
 *       orphan rather than updating the previous one,</li>
 *   <li>reported as "Loyalty config saved" throughout.</li>
 * </ul>
 *
 * <p>And the settings page defaulted its restaurant picker to "Global", so this was the default
 * action rather than an edge case — including for the welcome bonus, which would appear enabled and
 * never pay out.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyConfigBindingTest {

    @Mock private LoyaltyConfigRepository loyaltyConfigRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @InjectMocks private LoyaltyService loyaltyService;

    private static LoyaltyConfigRequest request(Long restaurantId) {
        LoyaltyConfigRequest req = new LoyaltyConfigRequest();
        req.setRestaurantId(restaurantId);
        req.setBonusRateValue(new BigDecimal("5"));
        return req;
    }

    @Test
    @DisplayName("a config with no restaurant is refused instead of saved where nobody can read it")
    void globalConfigWriteIsRefused() {
        assertThatThrownBy(() -> loyaltyService.upsertConfig(request(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Choose a restaurant");

        verify(loyaltyConfigRepository, never()).save(any());
    }

    /** The refusal has to say why, or it reads as an arbitrary validation rule. */
    @Test
    @DisplayName("the refusal explains that the config would not apply to anybody")
    void refusalExplainsWhy() {
        assertThatThrownBy(() -> loyaltyService.upsertConfig(request(null)))
                .hasMessageContaining("per-restaurant")
                .hasMessageContaining("not applied");
    }

    @Test
    @DisplayName("a config for a real restaurant saves against that restaurant")
    void perRestaurantConfigIsSaved() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(4L);
        when(loyaltyConfigRepository.findByRestaurant_IdAndEnabled(4L, true)).thenReturn(Optional.empty());
        when(restaurantRepository.findById(4L)).thenReturn(Optional.of(restaurant));
        when(loyaltyConfigRepository.save(any(LoyaltyConfig.class))).thenAnswer(i -> i.getArgument(0));

        LoyaltyConfig saved = loyaltyService.upsertConfig(request(4L));

        assertThat(saved.getRestaurant()).isNotNull();
        assertThat(saved.getRestaurant().getId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("an unknown restaurant is refused rather than silently creating a dangling config")
    void unknownRestaurantIsRefused() {
        when(loyaltyConfigRepository.findByRestaurant_IdAndEnabled(999L, true)).thenReturn(Optional.empty());
        when(restaurantRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loyaltyService.upsertConfig(request(999L)))
                .isInstanceOf(com.elcafe.exception.ResourceNotFoundException.class);
        verify(loyaltyConfigRepository, never()).save(any());
    }

    /** Saving twice must update the one config, not accumulate orphans — the old failure mode. */
    @Test
    @DisplayName("saving twice updates the same config rather than creating a second one")
    void repeatedSaveUpdatesInPlace() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(4L);
        LoyaltyConfig existing = LoyaltyConfig.builder().restaurant(restaurant).build();
        when(loyaltyConfigRepository.findByRestaurant_IdAndEnabled(4L, true))
                .thenReturn(Optional.of(existing));
        when(loyaltyConfigRepository.save(any(LoyaltyConfig.class))).thenAnswer(i -> i.getArgument(0));

        loyaltyService.upsertConfig(request(4L));
        loyaltyService.upsertConfig(request(4L));

        verify(restaurantRepository, never()).findById(any());   // never re-created
        assertThat(existing.getRestaurant().getId()).isEqualTo(4L);
    }
}

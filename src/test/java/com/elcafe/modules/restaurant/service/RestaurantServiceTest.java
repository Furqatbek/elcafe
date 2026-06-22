package com.elcafe.modules.restaurant.service;

import com.elcafe.modules.billing.entity.SubscriptionPlan;
import com.elcafe.modules.billing.repository.SubscriptionPlanRepository;
import com.elcafe.modules.financial.service.AccountService;
import com.elcafe.modules.restaurant.dto.RestaurantRequest;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.mapper.RestaurantMapper;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RestaurantServiceTest {

    @Mock private RestaurantRepository restaurantRepository;
    @Mock private RestaurantMapper restaurantMapper;
    @Mock private AccountService accountService;
    @Mock private SubscriptionPlanRepository planRepository;

    private RestaurantService service;

    @BeforeEach
    void setUp() {
        service = new RestaurantService(restaurantRepository, restaurantMapper, accountService, planRepository);
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(i -> i.getArgument(0));
    }

    private RestaurantRequest request() {
        RestaurantRequest req = new RestaurantRequest();
        req.setName("New Cafe");
        return req;
    }

    @Test
    @DisplayName("new restaurant lands on a 14-day Pro trial when the Pro plan exists")
    void createRestaurant_setsProTrial() {
        SubscriptionPlan pro = SubscriptionPlan.builder().code("pro").name("Pro").build();
        when(restaurantMapper.toEntity(any())).thenReturn(new Restaurant());
        when(planRepository.findByCode("pro")).thenReturn(Optional.of(pro));

        service.createRestaurant(request());

        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository).save(captor.capture());
        Restaurant saved = captor.getValue();
        assertThat(saved.getPlan()).isSameAs(pro);
        assertThat(saved.getIsTrial()).isTrue();
        assertThat(saved.getPlanStartedAt()).isNotNull();
        assertThat(saved.getPlanExpiresAt()).isNotNull();
        assertThat(ChronoUnit.DAYS.between(saved.getPlanStartedAt(), saved.getPlanExpiresAt())).isEqualTo(14);
    }

    @Test
    @DisplayName("no Pro plan seeded (tests / pre-seed) → restaurant created without a trial")
    void createRestaurant_noTrialWhenProMissing() {
        when(restaurantMapper.toEntity(any())).thenReturn(new Restaurant());
        when(planRepository.findByCode("pro")).thenReturn(Optional.empty());

        service.createRestaurant(request());

        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository).save(captor.capture());
        Restaurant saved = captor.getValue();
        assertThat(saved.getPlan()).isNull();
        assertThat(saved.getIsTrial()).isFalse(); // entity default; trial not applied
    }
}

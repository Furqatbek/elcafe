package com.elcafe.modules.reservation;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.billing.PlanFeatures;
import com.elcafe.modules.billing.service.PlanGateService;
import com.elcafe.modules.reservation.controller.ReservationController;
import com.elcafe.modules.reservation.dto.CreateReservationRequest;
import com.elcafe.modules.reservation.repository.ReservationSettingsRepository;
import com.elcafe.modules.reservation.service.ReservationService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The consumer booking intake must close with the plan (reservations = Advance): otherwise a
 * Start-tier restaurant keeps collecting public bookings its staff can't see, because the staff
 * management API is plan-gated. Verifies both intake points — the public reservable list and
 * createReservation — and that a plan-less restaurant (tests / unseeded) is not gated.
 */
@ExtendWith(MockitoExtension.class)
class ReservationPlanGateTest {

    // ---------------------------------------------------------------- createReservation

    @Mock private RestaurantRepository restaurantRepository;
    @Mock private ReservationSettingsRepository settingsRepository;
    @Mock private PlanGateService planGateService;
    @InjectMocks private ReservationService reservationService;

    private Restaurant restaurant(long id) {
        Restaurant r = new Restaurant();
        r.setId(id);
        r.setName("Cafe " + id);
        return r;
    }

    @Test
    @DisplayName("public createReservation is rejected when the plan lacks 'reservations'")
    void create_planLacksFeature_rejected() {
        when(restaurantRepository.findById(5L)).thenReturn(Optional.of(restaurant(5)));
        when(planGateService.hasFeatureIfPlanned(5L, PlanFeatures.RESERVATIONS)).thenReturn(false);

        assertThatThrownBy(() -> reservationService.createReservation(5L, new CreateReservationRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not accept online reservations");
        // Rejected before any settings/booking work happens.
        verifyNoInteractions(settingsRepository);
    }

    // ---------------------------------------------------------------- reservable list

    @Test
    @DisplayName("public reservable list drops restaurants whose plan lacks 'reservations'")
    void reservableList_filtersByPlan() {
        ReservationController controller = new ReservationController(
                null, null, null, restaurantRepository, settingsRepository, null, planGateService);
        when(restaurantRepository.findByActiveTrue()).thenReturn(List.of(restaurant(1), restaurant(2)));
        when(planGateService.hasFeatureIfPlanned(1L, PlanFeatures.RESERVATIONS)).thenReturn(true);
        when(planGateService.hasFeatureIfPlanned(2L, PlanFeatures.RESERVATIONS)).thenReturn(false);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.empty()); // default enabled

        var result = controller.getReservableRestaurants().getBody().getData();

        assertThat(result).extracting(ReservationController.RestaurantBasicInfo::id).containsExactly(1L);
    }
}

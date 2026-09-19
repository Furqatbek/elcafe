package com.elcafe.modules.partner.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.partner.dto.PartnerGrantRequest;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.outbox.PartnerMenuNotifier;
import com.elcafe.modules.partner.repository.IntegrationEventRepository;
import com.elcafe.modules.partner.repository.PartnerPriceRuleRepository;
import com.elcafe.modules.partner.repository.PartnerRepository;
import com.elcafe.modules.partner.repository.PartnerRestaurantRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Order push is the only capability that moves money, and it takes a partner's word for how the
 * customer paid.
 *
 * <p>ZBR told us their {@code paymentMode} is a constant: every order says {@code PREPAID} because
 * their apps do not set it yet. {@code PREPAID} creates the payment settled and prints a paid ticket,
 * so a cash order arriving under that constant has a counter hand giving food to a courier who owes
 * nothing. We answered that they should not switch a venue on for order push until it is real — a
 * promise somebody has to remember, and our own demo seeder had already forgotten it.
 *
 * <p>So it is a switch. These tests are what makes it one rather than a comment.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartnerAdminOrderPushSwitchTest {

    @Mock private PartnerRepository partnerRepository;
    @Mock private PartnerRestaurantRepository partnerRestaurantRepository;
    @Mock private PartnerPriceRuleRepository partnerPriceRuleRepository;
    @Mock private IntegrationEventRepository integrationEventRepository;
    @Mock private PartnerMenuNotifier partnerMenuNotifier;
    @Mock private RestaurantRepository restaurantRepository;

    @InjectMocks private PartnerAdminService service;

    private Partner partner;

    @BeforeEach
    void setUp() {
        partner = Partner.builder()
                .id(1L).name("ZBR").slug("zbr").apiKeyHash("hash").active(true)
                .paymentModeConfirmed(false)
                .build();
        when(partnerRepository.findById(1L)).thenReturn(Optional.of(partner));
        when(partnerRepository.save(any(Partner.class))).thenAnswer(call -> call.getArgument(0));
        when(restaurantRepository.findById(9L)).thenReturn(Optional.of(
                Restaurant.builder().id(9L).name("Demo Cafe").build()));
        when(partnerRestaurantRepository.findByPartnerIdAndRestaurantId(1L, 9L))
                .thenReturn(Optional.empty());
        when(partnerRestaurantRepository.save(any(PartnerRestaurant.class)))
                .thenAnswer(call -> call.getArgument(0));
    }

    private PartnerGrantRequest grant(boolean canPushOrders) {
        return PartnerGrantRequest.builder()
                .canReadMenu(true).canPushOrders(canPushOrders).build();
    }

    @Test
    @DisplayName("order push is refused while the partner's paymentMode is a constant")
    void pushIsRefusedWhilePaymentModeIsUnconfirmed() {
        assertThatThrownBy(() -> service.grantRestaurant(1L, 9L, grant(true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("paymentMode")
                // The operator has to be able to act on it, not just be stopped.
                .hasMessageContaining("Menu access is unaffected");

        verify(partnerRestaurantRepository, never()).save(any());
    }

    @Test
    @DisplayName("menu access is granted regardless — it never carried money")
    void menuAccessIsUnaffected() {
        service.grantRestaurant(1L, 9L, grant(false));

        ArgumentCaptor<PartnerRestaurant> saved = ArgumentCaptor.forClass(PartnerRestaurant.class);
        verify(partnerRestaurantRepository).save(saved.capture());
        assertThat(saved.getValue().getCanReadMenu()).isTrue();
        assertThat(saved.getValue().getCanPushOrders()).isFalse();
    }

    @Test
    @DisplayName("once the partner says the field is real, push can be granted")
    void pushIsAllowedOnceConfirmed() {
        partner.setPaymentModeConfirmed(true);

        service.grantRestaurant(1L, 9L, grant(true));

        ArgumentCaptor<PartnerRestaurant> saved = ArgumentCaptor.forClass(PartnerRestaurant.class);
        verify(partnerRestaurantRepository).save(saved.capture());
        assertThat(saved.getValue().getCanPushOrders()).isTrue();
    }

    @Test
    @DisplayName("un-confirming withdraws order push everywhere, rather than leaving it to memory")
    void unconfirmingWithdrawsPush() {
        // The case ZBR described: a venue turns out to take cash, or an app is rolled back. Revoking
        // venue by venue is the promise this switch replaced.
        partner.setPaymentModeConfirmed(true);
        PartnerRestaurant pushing = PartnerRestaurant.builder()
                .partnerId(1L).restaurantId(9L).canReadMenu(true).canPushOrders(true).active(true)
                .build();
        PartnerRestaurant readOnly = PartnerRestaurant.builder()
                .partnerId(1L).restaurantId(10L).canReadMenu(true).canPushOrders(false).active(true)
                .build();
        when(partnerRestaurantRepository.findByPartnerId(1L)).thenReturn(List.of(pushing, readOnly));

        service.setPaymentModeConfirmed(1L, false);

        assertThat(partner.getPaymentModeConfirmed()).isFalse();
        assertThat(pushing.getCanPushOrders()).isFalse();
        assertThat(readOnly.getCanReadMenu()).as("menu access is left alone").isTrue();
        verify(partnerRestaurantRepository).saveAll(List.of(pushing));
    }

    @Test
    @DisplayName("confirming it does not switch anything on by itself")
    void confirmingGrantsNothing() {
        // The flag says the field is trustworthy; it is not a decision to start sending a venue's
        // orders to anyone. That stays a separate, deliberate grant.
        service.setPaymentModeConfirmed(1L, true);

        assertThat(partner.getPaymentModeConfirmed()).isTrue();
        verify(partnerRestaurantRepository, never()).saveAll(any());
        verify(partnerRestaurantRepository, never()).save(any(PartnerRestaurant.class));
    }
}

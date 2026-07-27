package com.elcafe.modules.customer.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.customer.dto.StaffRegistrationRequest;
import com.elcafe.modules.customer.dto.StaffRegistrationResponse;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.service.LoyaltyService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V182 till-side registration. This door skips OTP by design, so what it must get right is everything
 * that bounds the damage of an unverified number: the acting employee is recorded, an existing phone is
 * matched rather than duplicated, and one guest can never be credited twice.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StaffAssistedRegistrationServiceTest {

    private static final Long TENANT = 3L;
    private static final Long OTHER_TENANT = 99L;
    private static final Long STAFF_USER = 42L;

    @Mock private CustomerRepository customerRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private LoyaltyService loyaltyService;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;

    @InjectMocks private StaffAssistedRegistrationService service;

    private StaffRegistrationRequest request(String phone, Long orderId) {
        return StaffRegistrationRequest.builder()
                .firstName("Dilnoza").phone(phone).orderId(orderId).build();
    }

    private void tenantIs(Long id) {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(id);
    }

    private void noExistingCustomer() {
        when(customerRepository.findByPhoneAndRestaurantId(any(), any())).thenReturn(Optional.empty());
        when(customerRepository.save(any())).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(11L);
            return c;
        });
    }

    private Order orderOf(Long restaurantId, Customer existingCustomer) {
        return Order.builder()
                .id(5L)
                .restaurant(Restaurant.builder().id(restaurantId).build())
                .customer(existingCustomer)
                .build();
    }

    @Test
    @DisplayName("a new guest records the employee who registered them — the audit trail for a door with no OTP")
    void recordsActingEmployee() {
        tenantIs(TENANT);
        noExistingCustomer();
        when(loyaltyService.grantRegistrationBonus(11L, TENANT)).thenReturn(new BigDecimal("20000"));

        StaffRegistrationResponse response = service.register(request("+998 90 111-22-33", null), STAFF_USER);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getRegisteredByUserId()).isEqualTo(STAFF_USER);
        // Normalised the same way the OTP door does, so one guest resolves to one record across both.
        assertThat(captor.getValue().getPhone()).isEqualTo("+998901112233");
        assertThat(response.getBonusGranted()).isEqualByComparingTo("20000");
        assertThat(response.getAlreadyRegistered()).isFalse();
    }

    @Test
    @DisplayName("a phone that already belongs to a customer is matched, never duplicated")
    void existingPhoneIsMatchedNotDuplicated() {
        tenantIs(TENANT);
        Customer existing = Customer.builder().id(8L).restaurantId(TENANT).phone("+998901112233")
                .firstName("Dilnoza").build();
        when(customerRepository.findByPhoneAndRestaurantId("+998901112233", TENANT))
                .thenReturn(Optional.of(existing));
        // Already had the welcome bonus through the other door.
        when(loyaltyService.grantRegistrationBonus(8L, TENANT)).thenReturn(BigDecimal.ZERO);

        StaffRegistrationResponse response = service.register(request("+998901112233", null), STAFF_USER);

        verify(customerRepository, never()).save(any());
        assertThat(response.getCustomerId()).isEqualTo(8L);
        assertThat(response.getAlreadyRegistered()).isTrue();
        // The cashier is told zero rather than promising a bonus that was never credited.
        assertThat(response.getBonusGranted()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("the order being paid is attached, so the visit counts as the new customer's")
    void linksTheOrderBeingPaid() {
        tenantIs(TENANT);
        noExistingCustomer();
        Order order = orderOf(TENANT, null);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        StaffRegistrationResponse response = service.register(request("+998901112233", 5L), STAFF_USER);

        assertThat(response.getOrderLinked()).isTrue();
        assertThat(order.getCustomer()).isNotNull();
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("another tenant's order is never linked, and the registration still succeeds")
    void foreignOrderIsNotLinked() {
        tenantIs(TENANT);
        noExistingCustomer();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(orderOf(OTHER_TENANT, null)));

        StaffRegistrationResponse response = service.register(request("+998901112233", 5L), STAFF_USER);

        assertThat(response.getOrderLinked()).isFalse();
        verify(orderRepository, never()).save(any());
        assertThat(response.getCustomerId()).isEqualTo(11L);   // registration itself still stands
    }

    @Test
    @DisplayName("an order already attributed to someone else is not stolen by this guest")
    void doesNotOverwriteAnExistingOrderCustomer() {
        tenantIs(TENANT);
        noExistingCustomer();
        Customer someoneElse = Customer.builder().id(77L).build();
        Order order = orderOf(TENANT, someoneElse);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        StaffRegistrationResponse response = service.register(request("+998901112233", 5L), STAFF_USER);

        assertThat(response.getOrderLinked()).isFalse();
        assertThat(order.getCustomer()).isSameAs(someoneElse);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("a platform account cannot register a guest into no restaurant")
    void platformAccountRejected() {
        tenantIs(null);

        assertThatThrownBy(() -> service.register(request("+998901112233", null), STAFF_USER))
                .isInstanceOf(BadRequestException.class);
        verify(customerRepository, never()).save(any());
        verify(loyaltyService, never()).grantRegistrationBonus(any(), any());
    }
}

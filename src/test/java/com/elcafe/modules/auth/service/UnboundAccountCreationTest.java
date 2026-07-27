package com.elcafe.modules.auth.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.auth.dto.CreateOperatorRequest;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.courier.dto.CreateCourierRequest;
import com.elcafe.modules.courier.repository.CourierLocationRepository;
import com.elcafe.modules.courier.repository.CourierProfileRepository;
import com.elcafe.modules.courier.repository.CourierWalletRepository;
import com.elcafe.modules.courier.service.CourierService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The staff creators, which between them accounted for two of the five ways an account could end up
 * signed-in and blind.
 *
 * <p>{@code OperatorService} deliberately fell back to a null binding for a SUPER_ADMIN creator,
 * commented as "preserving behaviour" — the behaviour preserved was an operator who could log in and
 * see nothing. {@code CourierService} was worse: it never set a binding at all, so <em>every</em>
 * courier it ever created was in that state.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnboundAccountCreationTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class Operators {

        @Mock private UserRepository userRepository;
        @Mock private PasswordEncoder passwordEncoder;
        @Mock private RestaurantAuthorizationService authz;
        @InjectMocks private OperatorService service;

        private CreateOperatorRequest request() {
            CreateOperatorRequest req = new CreateOperatorRequest();
            req.setEmail("op@qahvoon.uz");
            req.setPassword("password123");
            req.setFirstName("Op");
            req.setLastName("Erator");
            return req;
        }

        @Test
        @DisplayName("a platform account with no tenant cannot create an operator bound to nothing")
        void unboundOperatorRefused() {
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(authz.currentTenantScopeOrNull()).thenReturn(null);

            assertThatThrownBy(() -> service.createOperator(request()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("must belong to a restaurant");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("an operator created inside a restaurant is bound to it")
        void boundOperatorIsCreated() {
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(authz.currentTenantScopeOrNull()).thenReturn(4L);
            when(passwordEncoder.encode(any())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            service.createOperator(request());

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(saved.capture());
            assertThat(saved.getValue().getRestaurantId()).isEqualTo(4L);
            assertThat(saved.getValue().getRole()).isEqualTo(UserRole.OPERATOR);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class Couriers {

        @Mock private CourierProfileRepository courierProfileRepository;
        @Mock private CourierWalletRepository courierWalletRepository;
        @Mock private CourierLocationRepository courierLocationRepository;
        @Mock private UserRepository userRepository;
        @Mock private PasswordEncoder passwordEncoder;
        @Mock private RestaurantAuthorizationService authz;
        @InjectMocks private CourierService service;

        private CreateCourierRequest request() {
            CreateCourierRequest req = new CreateCourierRequest();
            req.setEmail("rider@qahvoon.uz");
            req.setPassword("password123");
            req.setFirstName("Ri");
            req.setLastName("Der");
            return req;
        }

        @Test
        @DisplayName("a courier can no longer be created with no restaurant at all")
        void unboundCourierRefused() {
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(authz.currentTenantScopeOrNull()).thenReturn(null);

            assertThatThrownBy(() -> service.createCourier(request()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("must belong to a restaurant");
            verify(userRepository, never()).save(any());
        }

        /** The binding this creator never set — every courier before this fix was unbound. */
        @Test
        @DisplayName("a courier created inside a restaurant is bound to it")
        void boundCourierIsCreated() {
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(authz.currentTenantScopeOrNull()).thenReturn(4L);
            when(passwordEncoder.encode(any())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            when(courierProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(courierWalletRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.createCourier(request());

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(saved.capture());
            assertThat(saved.getValue().getRestaurantId()).isEqualTo(4L);
            assertThat(saved.getValue().getRole()).isEqualTo(UserRole.COURIER);
        }
    }
}

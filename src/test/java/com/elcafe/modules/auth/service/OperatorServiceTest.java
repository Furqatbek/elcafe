package com.elcafe.modules.auth.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.auth.dto.CreateOperatorRequest;
import com.elcafe.modules.auth.dto.OperatorDTO;
import com.elcafe.modules.auth.dto.UpdateOperatorRequest;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperatorServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @InjectMocks private OperatorService operatorService;

    private User operator;

    @BeforeEach
    void setUp() {
        operator = User.builder()
                .id(1L).email("op@test.com").password("encoded")
                .firstName("Test").lastName("Operator")
                .role(UserRole.OPERATOR).active(true).build();
    }

    @Test @DisplayName("getAllOperators — returns paginated")
    void getAllOperators_returnsPage() {
        when(restaurantAuthorizationService.currentTenantReadScope()).thenReturn(null);
        when(userRepository.findByRole(UserRole.OPERATOR, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(operator), PageRequest.of(0, 10), 1));

        Page<OperatorDTO> result = operatorService.getAllOperators(PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("op@test.com");
    }

    @Test @DisplayName("getOperatorById — found")
    void getOperatorById_found() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        OperatorDTO result = operatorService.getOperatorById(1L);

        assertThat(result.getEmail()).isEqualTo("op@test.com");
        assertThat(result.getRole()).isEqualTo(UserRole.OPERATOR);
    }

    @Test @DisplayName("getOperatorById — not found throws")
    void getOperatorById_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> operatorService.getOperatorById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("getOperatorById — wrong role throws")
    void getOperatorById_wrongRole_throws() {
        User admin = User.builder().id(2L).role(UserRole.ADMIN).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> operatorService.getOperatorById(2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not an operator");
    }

    @Test @DisplayName("createOperator — success with encoded password")
    void createOperator_success() {
        CreateOperatorRequest request = new CreateOperatorRequest();
        request.setEmail("new@test.com");
        request.setPassword("password123");
        request.setFirstName("New");
        request.setLastName("Operator");

        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0); u.setId(2L); return u;
        });

        OperatorDTO result = operatorService.createOperator(request);

        assertThat(result.getEmail()).isEqualTo("new@test.com");
        assertThat(result.getRole()).isEqualTo(UserRole.OPERATOR);
        verify(passwordEncoder).encode("password123");
    }

    @Test @DisplayName("createOperator — duplicate email throws")
    void createOperator_duplicateEmail_throws() {
        CreateOperatorRequest request = new CreateOperatorRequest();
        request.setEmail("op@test.com");
        when(userRepository.existsByEmail("op@test.com")).thenReturn(true);

        assertThatThrownBy(() -> operatorService.createOperator(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");
    }

    @Test @DisplayName("updateOperator — updates fields selectively")
    void updateOperator_success() {
        UpdateOperatorRequest request = new UpdateOperatorRequest();
        request.setFirstName("Updated");
        request.setPhone("+998909876543");

        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        OperatorDTO result = operatorService.updateOperator(1L, request);

        assertThat(result.getFirstName()).isEqualTo("Updated");
        assertThat(result.getPhone()).isEqualTo("+998909876543");
        // lastName unchanged
        assertThat(result.getLastName()).isEqualTo("Operator");
    }

    @Test @DisplayName("deleteOperator — success")
    void deleteOperator_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        operatorService.deleteOperator(1L);

        verify(userRepository).delete(operator);
    }

    @Test @DisplayName("getAllOperators — scopes to caller's tenant when set (§3.3)")
    void getAllOperators_scopedToTenant() {
        when(restaurantAuthorizationService.currentTenantReadScope()).thenReturn(7L);
        when(userRepository.findByRoleAndRestaurantId(UserRole.OPERATOR, 7L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(operator), PageRequest.of(0, 10), 1));

        Page<OperatorDTO> result = operatorService.getAllOperators(PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(userRepository).findByRoleAndRestaurantId(UserRole.OPERATOR, 7L, PageRequest.of(0, 10));
        verify(userRepository, never()).findByRole(any(), any());
    }

    @Test @DisplayName("getOperatorById — cross-tenant denied (§3.3)")
    void getOperatorById_crossTenant_denied() {
        operator.setRestaurantId(2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(restaurantAuthorizationService).checkAccess(2L);

        assertThatThrownBy(() -> operatorService.getOperatorById(1L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}

package com.elcafe.modules.pos.cashdrawer.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.pos.cashdrawer.dto.*;
import com.elcafe.modules.pos.cashdrawer.entity.CashDrawer;
import com.elcafe.modules.pos.cashdrawer.entity.CashDrawerOperation;
import com.elcafe.modules.pos.cashdrawer.enums.CashOperationType;
import com.elcafe.modules.pos.cashdrawer.repository.CashDrawerOperationRepository;
import com.elcafe.modules.pos.cashdrawer.repository.CashDrawerRepository;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CashDrawerServiceTest {

    @Mock private CashDrawerRepository cashDrawerRepository;
    @Mock private CashDrawerOperationRepository operationRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmployeeShiftRepository shiftRepository;
    @InjectMocks private CashDrawerService cashDrawerService;

    private Restaurant restaurant;
    private CashDrawer drawer;
    private User operator;
    private EmployeeShift shift;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test");

        drawer = CashDrawer.builder()
                .id(1L).restaurant(restaurant).drawerName("Main Drawer")
                .expectedFloat(new BigDecimal("500000")).isActive(true).build();

        operator = new User();
        operator.setId(1L);
        operator.setEmail("cashier@test.com");
        operator.setFirstName("Test");
        operator.setLastName("Cashier");

        shift = new EmployeeShift();
        shift.setId(1L);
    }

    @Test @DisplayName("createCashDrawer — success")
    void createCashDrawer_success() {
        CreateCashDrawerRequest request = new CreateCashDrawerRequest();
        request.setDrawerName("Register 2");
        request.setExpectedFloat(new BigDecimal("300000"));
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(cashDrawerRepository.existsByRestaurantIdAndDrawerName(1L, "Register 2")).thenReturn(false);
        when(cashDrawerRepository.save(any())).thenAnswer(i -> { CashDrawer d = i.getArgument(0); d.setId(2L); return d; });

        CashDrawer result = cashDrawerService.createCashDrawer(1L, request);

        assertThat(result.getDrawerName()).isEqualTo("Register 2");
    }

    @Test @DisplayName("createCashDrawer — duplicate name throws")
    void createCashDrawer_duplicate_throws() {
        CreateCashDrawerRequest request = new CreateCashDrawerRequest();
        request.setDrawerName("Main Drawer");
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(cashDrawerRepository.existsByRestaurantIdAndDrawerName(1L, "Main Drawer")).thenReturn(true);

        assertThatThrownBy(() -> cashDrawerService.createCashDrawer(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test @DisplayName("getCashDrawers — returns active list")
    void getCashDrawers_returnsList() {
        when(cashDrawerRepository.findByRestaurantIdAndIsActiveTrue(1L)).thenReturn(List.of(drawer));
        assertThat(cashDrawerService.getCashDrawers(1L)).hasSize(1);
    }

    @Test @DisplayName("openDrawer — success")
    void openDrawer_success() {
        when(cashDrawerRepository.findById(1L)).thenReturn(Optional.of(drawer));
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(operationRepository.save(any())).thenAnswer(i -> { CashDrawerOperation o = i.getArgument(0); o.setId(1L); return o; });

        CashDrawerOperation result = cashDrawerService.openDrawer(1L, 1L, 1L, "Shift start");

        assertThat(result.getOperationType()).isEqualTo(CashOperationType.OPEN);
    }

    @Test @DisplayName("recordCashIn — records cash received")
    void addCash_success() {
        when(cashDrawerRepository.findById(1L)).thenReturn(Optional.of(drawer));
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(operationRepository.save(any())).thenAnswer(i -> { CashDrawerOperation o = i.getArgument(0); o.setId(2L); return o; });

        CashDrawerOperation result = cashDrawerService.recordCashIn(1L, new BigDecimal("80000"), 1L, 1L, null, null);

        assertThat(result.getOperationType()).isEqualTo(CashOperationType.CASH_IN);
        assertThat(result.getAmount()).isEqualByComparingTo("80000");
    }

    @Test @DisplayName("recordCashOut — records change given")
    void removeCash_success() {
        when(cashDrawerRepository.findById(1L)).thenReturn(Optional.of(drawer));
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(operationRepository.save(any())).thenAnswer(i -> { CashDrawerOperation o = i.getArgument(0); o.setId(3L); return o; });

        CashDrawerOperation result = cashDrawerService.recordCashOut(1L, new BigDecimal("20000"), 1L, 1L, null, null);

        assertThat(result.getOperationType()).isEqualTo(CashOperationType.CASH_OUT);
    }

    @Test @DisplayName("closeDrawer — calculates variance")
    void closeDrawer_success() {
        when(cashDrawerRepository.findById(1L)).thenReturn(Optional.of(drawer));
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(operationRepository.calculateNetCashMovement(1L)).thenReturn(new BigDecimal("200000"));
        when(operationRepository.save(any())).thenAnswer(i -> { CashDrawerOperation o = i.getArgument(0); o.setId(4L); return o; });

        DrawerCloseResult result = cashDrawerService.closeDrawer(1L, 1L, 1L, new BigDecimal("690000"));

        // Expected: 500000 (float) + 200000 (net) = 700000, Counted: 690000, Variance: -10000
        assertThat(result.getExpectedAmount()).isEqualByComparingTo("700000");
        assertThat(result.getCountedAmount()).isEqualByComparingTo("690000");
        assertThat(result.getVariance()).isEqualByComparingTo("-10000");
    }

    @Test @DisplayName("calculateExpectedCash — float + net movement")
    void getDrawerStatus_returnsBalance() {
        when(cashDrawerRepository.findById(1L)).thenReturn(Optional.of(drawer));
        when(operationRepository.calculateNetCashMovement(1L)).thenReturn(new BigDecimal("150000"));

        BigDecimal expected = cashDrawerService.calculateExpectedCash(1L, 1L);

        assertThat(expected).isEqualByComparingTo("650000"); // 500000 + 150000
    }

    @Test @DisplayName("getOperationHistory — returns paginated")
    void getOperations_returnsList() {
        CashDrawerOperation op = CashDrawerOperation.builder()
                .id(1L).cashDrawer(drawer).operationType(CashOperationType.CASH_IN)
                .amount(new BigDecimal("50000")).operator(operator).build();
        when(operationRepository.findByCashDrawerIdOrderByCreatedAtDesc(1L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(op), PageRequest.of(0, 20), 1));

        var result = cashDrawerService.getOperationHistory(1L, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test @DisplayName("getDrawerStatus — returns status with operations")
    void getDrawerStatus_returnsStatus() {
        when(cashDrawerRepository.findById(1L)).thenReturn(Optional.of(drawer));
        when(operationRepository.calculateNetCashMovement(1L)).thenReturn(new BigDecimal("100000"));
        when(operationRepository.findByCashDrawerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        DrawerStatusResponse result = cashDrawerService.getDrawerStatus(1L, 1L);

        assertThat(result.getDrawerName()).isEqualTo("Main Drawer");
        assertThat(result.getCurrentExpectedCash()).isEqualByComparingTo("600000");
    }

    @Test @DisplayName("recordPaidIn — records float added")
    void recordPaidIn_success() {
        when(cashDrawerRepository.findById(1L)).thenReturn(Optional.of(drawer));
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(operationRepository.save(any())).thenAnswer(i -> { CashDrawerOperation o = i.getArgument(0); o.setId(5L); return o; });

        CashDrawerOperation result = cashDrawerService.recordPaidIn(1L, new BigDecimal("100000"), 1L, 1L, "Additional float");

        assertThat(result.getOperationType()).isEqualTo(CashOperationType.PAID_IN);
    }

    @Test @DisplayName("recordCashDrop — records bank deposit")
    void recordCashDrop_success() {
        when(cashDrawerRepository.findById(1L)).thenReturn(Optional.of(drawer));
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(operationRepository.save(any())).thenAnswer(i -> { CashDrawerOperation o = i.getArgument(0); o.setId(6L); return o; });

        CashDrawerOperation result = cashDrawerService.recordCashDrop(1L, new BigDecimal("300000"), 1L, 1L, "End of shift");

        assertThat(result.getOperationType()).isEqualTo(CashOperationType.DROP);
    }
}

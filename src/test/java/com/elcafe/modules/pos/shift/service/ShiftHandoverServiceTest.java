package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.pos.shift.dto.ShiftHandoverDTO;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.enums.ShiftStatus;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftHandoverServiceTest {

    @Mock private EmployeeShiftRepository shiftRepository;
    @Mock private RestaurantTableRepository tableRepository;
    @Mock private OrderRepository orderRepository;
    @InjectMocks private ShiftHandoverService service;

    private Restaurant restaurant;
    private User employee;
    private EmployeeShift activeShift;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);

        employee = new User();
        employee.setId(10L);
        employee.setFirstName("Ali");
        employee.setLastName("K");

        activeShift = new EmployeeShift();
        activeShift.setId(100L);
        activeShift.setRestaurant(restaurant);
        activeShift.setEmployee(employee);
        activeShift.setStatus(ShiftStatus.ACTIVE);
        activeShift.setOpeningCash(new BigDecimal("500000"));
        activeShift.setTotalCashSales(new BigDecimal("750000"));
        activeShift.setTotalRefunds(BigDecimal.ZERO);
    }

    @Test @DisplayName("cash variance calculation: expected vs counted")
    void cashVariance() {
        when(shiftRepository.findById(100L)).thenReturn(Optional.of(activeShift));
        when(shiftRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Expected: 500000 (opening) + 750000 (cash sales) - 0 (refunds) = 1250000
        ShiftHandoverDTO result = service.completeHandover(100L, new BigDecimal("1240000"), "Slight shortage");

        assertThat(result.getExpectedCash()).isEqualByComparingTo("1250000");
        assertThat(result.getCountedCash()).isEqualByComparingTo("1240000");
        assertThat(result.getCashVariance()).isEqualByComparingTo("-10000");
        assertThat(result.isCompleted()).isTrue();
    }

    @Test @DisplayName("open tables listed correctly")
    void openTables() {
        when(shiftRepository.findById(100L)).thenReturn(Optional.of(activeShift));

        RestaurantTable t1 = new RestaurantTable();
        t1.setId(1L); t1.setTableNumber("4"); t1.setStatus(RestaurantTable.TableStatus.OCCUPIED);
        RestaurantTable t2 = new RestaurantTable();
        t2.setId(2L); t2.setTableNumber("7"); t2.setStatus(RestaurantTable.TableStatus.OCCUPIED);

        when(tableRepository.findByRestaurant_IdAndStatus(1L, RestaurantTable.TableStatus.OCCUPIED))
                .thenReturn(List.of(t1, t2));
        when(orderRepository.findByRestaurantIdAndStatusIn(eq(1L), any())).thenReturn(List.of());

        ShiftHandoverDTO result = service.prepareHandover(100L);

        assertThat(result.getOpenTableCount()).isEqualTo(2);
        assertThat(result.getOpenTables().get(0).getTableNumber()).isEqualTo("4");
        assertThat(result.getOpenTables().get(1).getTableNumber()).isEqualTo("7");
    }

    @Test @DisplayName("status transitions: ACTIVE → COMPLETED on handover")
    void statusTransition() {
        when(shiftRepository.findById(100L)).thenReturn(Optional.of(activeShift));
        when(shiftRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.completeHandover(100L, new BigDecimal("1250000"), null);

        assertThat(activeShift.getStatus()).isEqualTo(ShiftStatus.COMPLETED);
        assertThat(activeShift.getClockOut()).isNotNull();
    }

    @Test @DisplayName("pending orders listed correctly")
    void pendingOrders() {
        when(shiftRepository.findById(100L)).thenReturn(Optional.of(activeShift));
        when(tableRepository.findByRestaurant_IdAndStatus(eq(1L), any())).thenReturn(List.of());

        Order o1 = new Order(); o1.setId(45L); o1.setOrderNumber("ORD-045"); o1.setStatus(OrderStatus.PREPARING);
        Order o2 = new Order(); o2.setId(46L); o2.setOrderNumber("ORD-046"); o2.setStatus(OrderStatus.NEW);

        when(orderRepository.findByRestaurantIdAndStatusIn(eq(1L), any())).thenReturn(List.of(o1, o2));

        ShiftHandoverDTO result = service.prepareHandover(100L);

        assertThat(result.getPendingOrderCount()).isEqualTo(2);
        assertThat(result.getPendingOrders().get(0).getOrderNumber()).isEqualTo("ORD-045");
        assertThat(result.getPendingOrders().get(0).getStatus()).isEqualTo("PREPARING");
    }

    @Test @DisplayName("cannot handover a non-active shift")
    void cannotHandoverCompleted() {
        activeShift.setStatus(ShiftStatus.COMPLETED);
        when(shiftRepository.findById(100L)).thenReturn(Optional.of(activeShift));

        assertThatThrownBy(() -> service.completeHandover(100L, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");
    }
}

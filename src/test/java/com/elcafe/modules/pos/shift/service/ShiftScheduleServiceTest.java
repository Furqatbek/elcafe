package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.pos.shift.entity.ShiftSchedule;
import com.elcafe.modules.pos.shift.repository.ShiftScheduleRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.WorkingHours;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.restaurant.repository.WorkingHoursRepository;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShiftScheduleServiceTest {

    @Mock private ShiftScheduleRepository scheduleRepository;
    @Mock private WorkingHoursRepository workingHoursRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private UserRepository userRepository;
    @Mock private WaiterRepository waiterRepository;
    @InjectMocks private ShiftScheduleService service;

    private Restaurant restaurant;
    private User employee;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");

        employee = new User();
        employee.setId(10L);
        employee.setEmail("ali@test.com");

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(userRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(scheduleRepository.save(any())).thenAnswer(i -> {
            ShiftSchedule s = i.getArgument(0);
            if (s.getId() == null) s.setId(100L);
            return s;
        });
    }

    @Test @DisplayName("conflict detection — same employee, overlapping times")
    void conflictDetection() {
        ShiftSchedule existing = ShiftSchedule.builder()
                .id(1L).employee(employee).shiftDate(LocalDate.of(2026, 5, 5))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(16, 0))
                .status(ShiftSchedule.Status.SCHEDULED).build();

        when(scheduleRepository.findActiveByEmployeeAndDate(10L, LocalDate.of(2026, 5, 5)))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createSchedule(
                1L, "user", 10L, LocalDate.of(2026, 5, 5),
                LocalTime.of(12, 0), LocalTime.of(20, 0), null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Conflict");
    }

    @Test @DisplayName("no conflict — non-overlapping times")
    void noConflict() {
        ShiftSchedule existing = ShiftSchedule.builder()
                .id(1L).employee(employee).shiftDate(LocalDate.of(2026, 5, 5))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
                .status(ShiftSchedule.Status.SCHEDULED).build();

        when(scheduleRepository.findActiveByEmployeeAndDate(10L, LocalDate.of(2026, 5, 5)))
                .thenReturn(List.of(existing));

        ShiftSchedule result = service.createSchedule(
                1L, "user", 10L, LocalDate.of(2026, 5, 5),
                LocalTime.of(14, 0), LocalTime.of(20, 0), "WAITER", null, null);

        assertThat(result).isNotNull();
        assertThat(result.getStartTime()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test @DisplayName("copy week creates correct records")
    void copyWeek() {
        LocalDate source = LocalDate.of(2026, 5, 5); // Monday
        LocalDate target = LocalDate.of(2026, 5, 12); // Next Monday

        ShiftSchedule s1 = ShiftSchedule.builder()
                .id(1L).employee(employee).restaurant(restaurant)
                .shiftDate(source).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0))
                .status(ShiftSchedule.Status.SCHEDULED).build();
        ShiftSchedule s2 = ShiftSchedule.builder()
                .id(2L).employee(employee).restaurant(restaurant)
                .shiftDate(source.plusDays(2)).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0))
                .status(ShiftSchedule.Status.SCHEDULED).build();

        when(scheduleRepository.findByRestaurantIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
                1L, source, source.plusDays(6))).thenReturn(List.of(s1, s2));
        when(scheduleRepository.findActiveByEmployeeAndDate(any(), any())).thenReturn(List.of());

        List<ShiftSchedule> result = service.copyWeek(1L, source, target, null);

        assertThat(result).hasSize(2);
    }

    @Test @DisplayName("auto-fill from WorkingHours")
    void autoFill() {
        LocalDate monday = LocalDate.of(2026, 5, 4); // 2026-05-04 is a Monday (2026-05-05 is a Tuesday)

        WorkingHours wh = new WorkingHours();
        wh.setUser(employee);
        wh.setDayOfWeek(DayOfWeek.MONDAY);
        wh.setStartTime(LocalTime.of(8, 0));
        wh.setEndTime(LocalTime.of(16, 0));
        wh.setActive(true);

        when(workingHoursRepository.findByRestaurant_Id(1L)).thenReturn(List.of(wh));
        when(scheduleRepository.findActiveByEmployeeAndDate(any(), any())).thenReturn(List.of());

        List<ShiftSchedule> result = service.autoFillFromWorkingHours(1L, monday, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getShiftDate()).isEqualTo(monday);
        assertThat(result.get(0).getStartTime()).isEqualTo(LocalTime.of(8, 0));
    }
}

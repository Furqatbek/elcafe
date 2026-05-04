package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.pos.shift.entity.ShiftSchedule;
import com.elcafe.modules.pos.shift.entity.ShiftSwapRequest;
import com.elcafe.modules.pos.shift.repository.ShiftScheduleRepository;
import com.elcafe.modules.pos.shift.repository.ShiftSwapRequestRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftSwapServiceTest {

    @Mock private ShiftSwapRequestRepository swapRepository;
    @Mock private ShiftScheduleRepository scheduleRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private ShiftSwapService service;

    private Restaurant restaurant;
    private User ali;
    private User jasur;
    private User manager;
    private ShiftSchedule schedule;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);

        ali = new User();
        ali.setId(10L);
        ali.setFirstName("Ali");
        ali.setLastName("K");

        jasur = new User();
        jasur.setId(11L);
        jasur.setFirstName("Jasur");
        jasur.setLastName("M");

        manager = new User();
        manager.setId(99L);

        schedule = ShiftSchedule.builder()
                .id(50L).employee(ali).restaurant(restaurant)
                .shiftDate(LocalDate.of(2026, 5, 5))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(16, 0))
                .status(ShiftSchedule.Status.SCHEDULED).build();

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(userRepository.findById(10L)).thenReturn(Optional.of(ali));
        when(userRepository.findById(11L)).thenReturn(Optional.of(jasur));
        when(userRepository.findById(99L)).thenReturn(Optional.of(manager));
        when(scheduleRepository.findById(50L)).thenReturn(Optional.of(schedule));
        when(swapRepository.save(any())).thenAnswer(i -> {
            ShiftSwapRequest r = i.getArgument(0);
            if (r.getId() == null) r.setId(200L);
            return r;
        });
    }

    @Test @DisplayName("request created with PENDING status")
    void createRequest() {
        ShiftSwapRequest result = service.createRequest(1L, 10L, 11L, 50L,
                LocalDate.of(2026, 5, 5), "Family event");

        assertThat(result.getStatus()).isEqualTo(ShiftSwapRequest.Status.PENDING);
        assertThat(result.getRequestingEmployee().getId()).isEqualTo(10L);
        assertThat(result.getTargetEmployee().getId()).isEqualTo(11L);
        assertThat(result.getReason()).isEqualTo("Family event");
    }

    @Test @DisplayName("target accepts → status ACCEPTED")
    void acceptRequest() {
        ShiftSwapRequest request = ShiftSwapRequest.builder()
                .id(200L).status(ShiftSwapRequest.Status.PENDING)
                .requestingEmployee(ali).targetEmployee(jasur)
                .restaurant(restaurant).schedule(schedule)
                .shiftDate(LocalDate.of(2026, 5, 5)).build();
        when(swapRepository.findById(200L)).thenReturn(Optional.of(request));

        ShiftSwapRequest result = service.acceptRequest(200L);

        assertThat(result.getStatus()).isEqualTo(ShiftSwapRequest.Status.ACCEPTED);
    }

    @Test @DisplayName("manager approves → schedule reassigned to target")
    void managerApproves() {
        ShiftSwapRequest request = ShiftSwapRequest.builder()
                .id(200L).status(ShiftSwapRequest.Status.ACCEPTED)
                .requestingEmployee(ali).targetEmployee(jasur)
                .restaurant(restaurant).schedule(schedule)
                .shiftDate(LocalDate.of(2026, 5, 5)).build();
        when(swapRepository.findById(200L)).thenReturn(Optional.of(request));

        ShiftSwapRequest result = service.approveRequest(200L, 99L);

        assertThat(result.getStatus()).isEqualTo(ShiftSwapRequest.Status.APPROVED);
        assertThat(result.getApprovedBy().getId()).isEqualTo(99L);
        // Schedule should be reassigned to jasur
        assertThat(schedule.getEmployee().getId()).isEqualTo(11L);
        verify(scheduleRepository).save(schedule);
    }

    @Test @DisplayName("manager rejects → original schedule unchanged")
    void managerRejects() {
        ShiftSwapRequest request = ShiftSwapRequest.builder()
                .id(200L).status(ShiftSwapRequest.Status.PENDING)
                .requestingEmployee(ali).targetEmployee(jasur)
                .restaurant(restaurant).schedule(schedule)
                .shiftDate(LocalDate.of(2026, 5, 5)).build();
        when(swapRepository.findById(200L)).thenReturn(Optional.of(request));

        ShiftSwapRequest result = service.rejectRequest(200L);

        assertThat(result.getStatus()).isEqualTo(ShiftSwapRequest.Status.REJECTED);
        // Schedule NOT reassigned — ali still owns it
        assertThat(schedule.getEmployee().getId()).isEqualTo(10L);
        verify(scheduleRepository, never()).save(any());
    }
}

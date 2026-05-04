package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.entity.ShiftRules;
import com.elcafe.modules.pos.shift.entity.ShiftSchedule;
import com.elcafe.modules.pos.shift.enums.ShiftStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShiftNotificationServiceTest {

    private ShiftNotificationService service;
    private ShiftRules rules;
    private User employee;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        service = new ShiftNotificationService();

        employee = new User();
        employee.setId(10L);
        employee.setFirstName("Ali");
        employee.setLastName("K");

        restaurant = new Restaurant();
        restaurant.setId(1L);

        rules = ShiftRules.builder()
                .maxShiftHours(8)
                .notifyOvertimeAtHours(7)
                .minBreakDurationMinutes(30)
                .build();
    }

    @Test @DisplayName("late employee triggers manager notification")
    void lateEmployee() {
        ShiftSchedule schedule = ShiftSchedule.builder()
                .employee(employee)
                .shiftDate(LocalDate.now())
                .startTime(LocalTime.now().minusMinutes(15)) // 15 min ago = 15 min late
                .endTime(LocalTime.now().plusHours(8))
                .status(ShiftSchedule.Status.SCHEDULED)
                .build();

        ShiftNotificationService.ShiftNotification notification =
                service.checkLateEmployee(schedule, false);

        assertThat(notification).isNotNull();
        assertThat(notification.type()).isEqualTo(ShiftNotificationService.NotificationType.EMPLOYEE_LATE);
        assertThat(notification.recipientRole()).isEqualTo("MANAGER");
        assertThat(notification.message()).contains("Ali");
    }

    @Test @DisplayName("not late when shift is active")
    void notLateWhenActive() {
        ShiftSchedule schedule = ShiftSchedule.builder()
                .employee(employee)
                .shiftDate(LocalDate.now())
                .startTime(LocalTime.now().minusMinutes(15))
                .endTime(LocalTime.now().plusHours(8))
                .status(ShiftSchedule.Status.SCHEDULED)
                .build();

        ShiftNotificationService.ShiftNotification notification =
                service.checkLateEmployee(schedule, true); // has active shift

        assertThat(notification).isNull();
    }

    @Test @DisplayName("overtime warning fires at threshold")
    void overtimeWarning() {
        EmployeeShift shift = new EmployeeShift();
        shift.setEmployee(employee);
        shift.setStatus(ShiftStatus.ACTIVE);
        shift.setClockIn(OffsetDateTime.now(ZoneOffset.UTC).minusHours(7).minusMinutes(15)); // 7h15m worked

        ShiftNotificationService.ShiftNotification notification =
                service.checkOvertimeWarning(shift, rules);

        assertThat(notification).isNotNull();
        assertThat(notification.type()).isEqualTo(ShiftNotificationService.NotificationType.APPROACHING_OVERTIME);
        assertThat(notification.recipientId()).isEqualTo(10L);
    }

    @Test @DisplayName("no overtime warning when below threshold")
    void noOvertimeWarning() {
        EmployeeShift shift = new EmployeeShift();
        shift.setEmployee(employee);
        shift.setStatus(ShiftStatus.ACTIVE);
        shift.setClockIn(OffsetDateTime.now(ZoneOffset.UTC).minusHours(5)); // 5h < 7h notify

        ShiftNotificationService.ShiftNotification notification =
                service.checkOvertimeWarning(shift, rules);

        assertThat(notification).isNull();
    }

    @Test @DisplayName("schedule publish notifies all employees")
    void schedulePublish() {
        User jasur = new User();
        jasur.setId(11L);
        jasur.setFirstName("Jasur");
        jasur.setLastName("M");

        List<ShiftSchedule> schedules = List.of(
                ShiftSchedule.builder()
                        .employee(employee)
                        .shiftDate(LocalDate.of(2026, 5, 5))
                        .startTime(LocalTime.of(8, 0))
                        .endTime(LocalTime.of(16, 0))
                        .status(ShiftSchedule.Status.SCHEDULED)
                        .build(),
                ShiftSchedule.builder()
                        .employee(jasur)
                        .shiftDate(LocalDate.of(2026, 5, 5))
                        .startTime(LocalTime.of(12, 0))
                        .endTime(LocalTime.of(20, 0))
                        .status(ShiftSchedule.Status.SCHEDULED)
                        .build(),
                ShiftSchedule.builder()
                        .employee(employee)
                        .shiftDate(LocalDate.of(2026, 5, 6))
                        .startTime(LocalTime.of(8, 0))
                        .endTime(LocalTime.of(16, 0))
                        .status(ShiftSchedule.Status.CANCELLED) // should be skipped
                        .build()
        );

        List<ShiftNotificationService.ShiftNotification> notifications =
                service.notifySchedulePublished(schedules, "May 5-11");

        assertThat(notifications).hasSize(2); // cancelled skipped
        assertThat(notifications.get(0).recipientId()).isEqualTo(10L);
        assertThat(notifications.get(1).recipientId()).isEqualTo(11L);
        assertThat(notifications.get(0).type()).isEqualTo(ShiftNotificationService.NotificationType.SCHEDULE_PUBLISHED);
    }
}

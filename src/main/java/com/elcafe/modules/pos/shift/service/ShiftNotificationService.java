package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.entity.ShiftRules;
import com.elcafe.modules.pos.shift.entity.ShiftSchedule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles shift-related notifications:
 * - Late employee alerts to manager
 * - Overtime warnings to employee + manager
 * - Break exceeded alerts
 * - Shift starting reminders
 * - Schedule publish notifications
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftNotificationService {

    public enum NotificationType {
        SHIFT_STARTING_SOON,
        EMPLOYEE_LATE,
        BREAK_EXCEEDED,
        APPROACHING_OVERTIME,
        SHIFT_HANDOVER_NEEDED,
        END_OF_DAY_REPORT,
        SCHEDULE_PUBLISHED,
        SHIFT_SWAP_REQUEST
    }

    public record ShiftNotification(
            NotificationType type,
            String title,
            String message,
            Long recipientId,
            String recipientRole
    ) {}

    /**
     * Check if employee is late and generate manager notification.
     * Late = current time > scheduled start + 10 minutes and no active shift.
     */
    public ShiftNotification checkLateEmployee(ShiftSchedule schedule, boolean hasActiveShift) {
        if (hasActiveShift) return null;

        LocalTime now = LocalTime.now();
        LocalTime scheduledStart = schedule.getStartTime();
        long minutesLate = ChronoUnit.MINUTES.between(scheduledStart, now);

        if (schedule.getShiftDate().equals(LocalDate.now()) && minutesLate > 10) {
            String empName = schedule.getEmployee().getFullName();
            return new ShiftNotification(
                    NotificationType.EMPLOYEE_LATE,
                    "Employee Late",
                    String.format("%s is %d minutes late (scheduled %s)",
                            empName, minutesLate, scheduledStart),
                    null, // manager notification — no specific recipient
                    "MANAGER"
            );
        }
        return null;
    }

    /**
     * Check if shift is approaching overtime and generate notification.
     */
    public ShiftNotification checkOvertimeWarning(EmployeeShift shift, ShiftRules rules) {
        long workedMinutes = shift.getWorkedMinutes();
        long notifyAtMinutes = (long) rules.getNotifyOvertimeAtHours() * 60;

        if (workedMinutes >= notifyAtMinutes && workedMinutes < (long) rules.getMaxShiftHours() * 60) {
            String empName = shift.getEmployee().getFullName();
            long remaining = (long) rules.getMaxShiftHours() * 60 - workedMinutes;
            return new ShiftNotification(
                    NotificationType.APPROACHING_OVERTIME,
                    "Approaching Overtime",
                    String.format("%s has %d minutes until max shift hours (%dh)",
                            empName, remaining, rules.getMaxShiftHours()),
                    shift.getEmployee().getId(),
                    "EMPLOYEE"
            );
        }
        return null;
    }

    /**
     * Check if break has exceeded max duration.
     */
    public ShiftNotification checkBreakExceeded(EmployeeShift shift, ShiftRules rules, long currentBreakMinutes) {
        if (currentBreakMinutes > rules.getMinBreakDurationMinutes() * 2) {
            String empName = shift.getEmployee().getFullName();
            return new ShiftNotification(
                    NotificationType.BREAK_EXCEEDED,
                    "Break Exceeded",
                    String.format("%s has been on break for %d minutes (max recommended: %d)",
                            empName, currentBreakMinutes, rules.getMinBreakDurationMinutes()),
                    null,
                    "MANAGER"
            );
        }
        return null;
    }

    /**
     * Generate notifications for schedule published event.
     */
    public List<ShiftNotification> notifySchedulePublished(List<ShiftSchedule> schedules, String weekLabel) {
        List<ShiftNotification> notifications = new ArrayList<>();

        for (ShiftSchedule schedule : schedules) {
            if (schedule.getStatus() == ShiftSchedule.Status.CANCELLED) continue;

            notifications.add(new ShiftNotification(
                    NotificationType.SCHEDULE_PUBLISHED,
                    "Schedule Published",
                    String.format("Your shift for %s: %s - %s",
                            weekLabel, schedule.getStartTime(), schedule.getEndTime()),
                    schedule.getEmployee().getId(),
                    "EMPLOYEE"
            ));
        }

        log.info("Generated {} schedule publish notifications for week {}", notifications.size(), weekLabel);
        return notifications;
    }
}

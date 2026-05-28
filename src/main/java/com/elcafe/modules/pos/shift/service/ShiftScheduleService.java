package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.pos.shift.entity.ShiftSchedule;
import com.elcafe.modules.pos.shift.repository.ShiftScheduleRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.WorkingHours;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.restaurant.repository.WorkingHoursRepository;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftScheduleService {

    private final ShiftScheduleRepository scheduleRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final WaiterRepository waiterRepository;

    @Transactional(readOnly = true)
    public List<ShiftSchedule> getWeekSchedule(Long restaurantId, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        return scheduleRepository.findByRestaurantIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
                restaurantId, weekStart, weekEnd);
    }

    /**
     * Create a schedule entry for a User (operator/manager) or a Waiter,
     * but never both. The subject is selected by {@code employeeType}
     * ("user" or "waiter"). Conflicts on the same subject + date refuse
     * the new row.
     */
    @Transactional
    public ShiftSchedule createSchedule(Long restaurantId, String employeeType, Long subjectId,
                                         LocalDate date, LocalTime startTime, LocalTime endTime,
                                         String role, String notes, Long createdById) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        User employee = null;
        Waiter waiter = null;
        if ("waiter".equalsIgnoreCase(employeeType)) {
            waiter = waiterRepository.findById(subjectId)
                    .orElseThrow(() -> new RuntimeException("Waiter not found"));
            for (ShiftSchedule s : scheduleRepository.findActiveByWaiterAndDate(subjectId, date)) {
                if (s.overlaps(startTime, endTime)) {
                    throw new IllegalStateException(String.format(
                            "Conflict: waiter already scheduled %s-%s on %s",
                            s.getStartTime(), s.getEndTime(), date));
                }
            }
        } else {
            employee = userRepository.findById(subjectId)
                    .orElseThrow(() -> new RuntimeException("Employee not found"));
            for (ShiftSchedule s : scheduleRepository.findActiveByEmployeeAndDate(subjectId, date)) {
                if (s.overlaps(startTime, endTime)) {
                    throw new IllegalStateException(String.format(
                            "Conflict: employee already scheduled %s-%s on %s",
                            s.getStartTime(), s.getEndTime(), date));
                }
            }
        }

        User createdBy = createdById != null ? userRepository.findById(createdById).orElse(null) : null;

        ShiftSchedule schedule = ShiftSchedule.builder()
                .restaurant(restaurant)
                .employee(employee)
                .waiter(waiter)
                .shiftDate(date)
                .startTime(startTime)
                .endTime(endTime)
                .role(role)
                .notes(notes)
                .createdBy(createdBy)
                .build();

        schedule = scheduleRepository.save(schedule);
        String name = employee != null ? employee.getFullName()
                : (waiter != null ? waiter.getName() : "?");
        log.info("Created shift schedule: {} for {} on {}", schedule.getId(), name, date);
        return schedule;
    }

    @Transactional
    public void cancelSchedule(Long scheduleId) {
        ShiftSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Schedule not found"));
        schedule.setStatus(ShiftSchedule.Status.CANCELLED);
        scheduleRepository.save(schedule);
        log.info("Cancelled shift schedule {}", scheduleId);
    }

    @Transactional
    public void deleteSchedule(Long scheduleId) {
        scheduleRepository.deleteById(scheduleId);
    }

    /**
     * Copy previous week's schedule to target week. Waiter rows are
     * cloned as waiter rows; user rows stay user rows.
     */
    @Transactional
    public List<ShiftSchedule> copyWeek(Long restaurantId, LocalDate sourceWeekStart, LocalDate targetWeekStart, Long createdById) {
        LocalDate sourceWeekEnd = sourceWeekStart.plusDays(6);
        List<ShiftSchedule> source = scheduleRepository
                .findByRestaurantIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(restaurantId, sourceWeekStart, sourceWeekEnd);

        List<ShiftSchedule> created = new ArrayList<>();
        for (ShiftSchedule s : source) {
            if (s.getStatus() == ShiftSchedule.Status.CANCELLED) continue;

            int dayOffset = (int) (s.getShiftDate().toEpochDay() - sourceWeekStart.toEpochDay());
            LocalDate targetDate = targetWeekStart.plusDays(dayOffset);

            String type;
            Long subjectId;
            if (s.getWaiter() != null) {
                type = "waiter"; subjectId = s.getWaiter().getId();
            } else if (s.getEmployee() != null) {
                type = "user"; subjectId = s.getEmployee().getId();
            } else {
                continue;
            }

            try {
                ShiftSchedule newSchedule = createSchedule(
                        restaurantId, type, subjectId, targetDate,
                        s.getStartTime(), s.getEndTime(), s.getRole(), s.getNotes(), createdById);
                created.add(newSchedule);
            } catch (IllegalStateException e) {
                log.warn("Skipping conflict during copy: {}", e.getMessage());
            }
        }

        log.info("Copied {} schedules from week {} to week {}", created.size(), sourceWeekStart, targetWeekStart);
        return created;
    }

    /**
     * Auto-fill a week from recurring WorkingHours (User-only — there's
     * no waiter-side recurring-hours config today).
     */
    @Transactional
    public List<ShiftSchedule> autoFillFromWorkingHours(Long restaurantId, LocalDate weekStart, Long createdById) {
        List<WorkingHours> workingHours = workingHoursRepository.findByRestaurant_Id(restaurantId);
        List<ShiftSchedule> created = new ArrayList<>();

        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            LocalDate date = weekStart.plusDays(dayOffset);
            DayOfWeek dow = date.getDayOfWeek();

            for (WorkingHours wh : workingHours) {
                if (wh.getDayOfWeek() != dow || !wh.getActive()) continue;

                try {
                    ShiftSchedule schedule = createSchedule(
                            restaurantId, "user", wh.getUser().getId(), date,
                            wh.getStartTime(), wh.getEndTime(), null, null, createdById);
                    created.add(schedule);
                } catch (IllegalStateException e) {
                    log.debug("Skipping auto-fill conflict: {}", e.getMessage());
                }
            }
        }

        log.info("Auto-filled {} schedules for week {} from WorkingHours", created.size(), weekStart);
        return created;
    }

    /**
     * Returns the earliest scheduled start time today for the given
     * subject, or null if nothing matches. Used by the clock-in flow
     * to auto-populate EmployeeShift.scheduledStart so the late-penalty
     * logic has a reference time without any manual entry.
     */
    @Transactional(readOnly = true)
    public LocalTime findTodayScheduledStart(Long userId, Long waiterId, LocalDate date) {
        List<ShiftSchedule> matches;
        if (waiterId != null) {
            matches = scheduleRepository.findActiveByWaiterAndDate(waiterId, date);
        } else if (userId != null) {
            matches = scheduleRepository.findActiveByEmployeeAndDate(userId, date);
        } else {
            return null;
        }
        return matches.stream()
                .map(ShiftSchedule::getStartTime)
                .min(LocalTime::compareTo)
                .orElse(null);
    }
}

package com.elcafe.modules.pos.shift.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.pos.shift.entity.ShiftSchedule;
import com.elcafe.modules.pos.shift.service.ShiftScheduleService;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/shift-schedules")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class ShiftScheduleController {

    private final ShiftScheduleService scheduleService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ApiResponse<List<ShiftSchedule>>> getWeekSchedule(
            @PathVariable Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        List<ShiftSchedule> schedules = scheduleService.getWeekSchedule(restaurantId, weekStart);
        return ResponseEntity.ok(ApiResponse.success("Schedule retrieved", schedules));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ShiftSchedule>> createSchedule(@RequestBody CreateScheduleRequest request) {
        restaurantAuthorizationService.checkAccess(request.getRestaurantId());
        // employeeType picks the subject table:
        //   "waiter" → schedule a Waiter (uses waiters.id)
        //   anything else (or null) → schedule a User (uses users.id)
        String type = request.employeeType != null ? request.employeeType : "user";
        Long subjectId = "waiter".equalsIgnoreCase(type) && request.waiterId != null
                ? request.waiterId
                : request.employeeId;
        log.info("Creating shift schedule for {} {} on {}", type, subjectId, request.shiftDate);
        ShiftSchedule schedule = scheduleService.createSchedule(
                request.restaurantId, type, subjectId, request.shiftDate,
                request.startTime, request.endTime, request.role, request.notes, request.createdById);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Shift scheduled", schedule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ShiftSchedule>> updateSchedule(
            @PathVariable Long id, @RequestBody UpdateScheduleRequest request) {
        ShiftSchedule updated = scheduleService.updateSchedule(
                id, request.shiftDate, request.startTime, request.endTime,
                request.role, request.notes);
        return ResponseEntity.ok(ApiResponse.success("Schedule updated", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(@PathVariable Long id) {
        scheduleService.deleteSchedule(id);
        return ResponseEntity.ok(ApiResponse.success("Schedule deleted", null));
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<ShiftScheduleService.BulkResult>> bulkCreate(
            @RequestBody BulkCreateRequest request) {
        restaurantAuthorizationService.checkAccess(request.getRestaurantId());
        java.util.List<ShiftScheduleService.Subject> subjects = request.employees == null
                ? java.util.List.of()
                : request.employees.stream()
                    .map(e -> new ShiftScheduleService.Subject(
                            e.type != null ? e.type : "user", e.id))
                    .toList();
        ShiftScheduleService.BulkResult result = scheduleService.bulkCreate(
                request.restaurantId, subjects, request.weekdays,
                request.fromDate, request.toDate,
                request.startTime, request.endTime,
                request.role, request.notes, request.createdById);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Bulk created: " + result.created() + " (skipped " + result.skipped() + ")",
                        result));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelSchedule(@PathVariable Long id) {
        scheduleService.cancelSchedule(id);
        return ResponseEntity.ok(ApiResponse.success("Schedule cancelled", null));
    }

    @PostMapping("/restaurant/{restaurantId}/copy-week")
    public ResponseEntity<ApiResponse<List<ShiftSchedule>>> copyWeek(
            @PathVariable Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sourceWeek,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetWeek,
            @RequestParam(required = false) Long createdById) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        List<ShiftSchedule> created = scheduleService.copyWeek(restaurantId, sourceWeek, targetWeek, createdById);
        return ResponseEntity.ok(ApiResponse.success("Week copied: " + created.size() + " schedules", created));
    }

    @PostMapping("/restaurant/{restaurantId}/auto-fill")
    public ResponseEntity<ApiResponse<List<ShiftSchedule>>> autoFill(
            @PathVariable Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(required = false) Long createdById) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        List<ShiftSchedule> created = scheduleService.autoFillFromWorkingHours(restaurantId, weekStart, createdById);
        return ResponseEntity.ok(ApiResponse.success("Auto-filled: " + created.size() + " schedules", created));
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UpdateScheduleRequest {
        private LocalDate shiftDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private String role;
        private String notes;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BulkCreateRequest {
        private Long restaurantId;
        private java.util.List<EmployeeRef> employees;
        // ISO day-of-week numbers (1=Monday..7=Sunday) to schedule on.
        private java.util.List<Integer> weekdays;
        private LocalDate fromDate;
        private LocalDate toDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private String role;
        private String notes;
        private Long createdById;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EmployeeRef {
        private String type;
        private Long id;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateScheduleRequest {
        private Long restaurantId;
        private Long employeeId;
        private Long waiterId;
        // "user" or "waiter"; defaults to "user" for backward compat.
        private String employeeType;
        private LocalDate shiftDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private String role;
        private String notes;
        private Long createdById;
    }
}

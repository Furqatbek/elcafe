package com.elcafe.modules.reservation.controller;

import com.elcafe.modules.reservation.dto.AvailabilityResponse;
import com.elcafe.modules.reservation.dto.CreateReservationRequest;
import com.elcafe.modules.reservation.dto.ReservationResponse;
import com.elcafe.modules.reservation.dto.TableAvailabilityResponse;
import com.elcafe.modules.reservation.entity.ReservationSettings;
import com.elcafe.modules.reservation.repository.ReservationSettingsRepository;
import com.elcafe.modules.reservation.service.AvailabilityService;
import com.elcafe.modules.reservation.service.ReservationService;
import com.elcafe.modules.reservation.service.TableAvailabilityService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Table reservation management")
public class ReservationController {

    private final ReservationService reservationService;
    private final AvailabilityService availabilityService;
    private final TableAvailabilityService tableAvailabilityService;
    private final RestaurantRepository restaurantRepository;
    private final ReservationSettingsRepository reservationSettingsRepository;

    // ========== Public Endpoints (for customers) ==========

    @GetMapping("/public/restaurants/reservable")
    @Operation(summary = "Get restaurants accepting reservations", description = "Get list of restaurants that accept online reservations")
    public ResponseEntity<ApiResponse<List<RestaurantBasicInfo>>> getReservableRestaurants() {
        List<Restaurant> restaurants = restaurantRepository.findByActiveTrue();
        List<RestaurantBasicInfo> result = restaurants.stream()
                .filter(r -> {
                    return reservationSettingsRepository.findByRestaurantId(r.getId())
                            .map(ReservationSettings::getEnabled)
                            .orElse(true); // Default to true if no settings
                })
                .map(r -> new RestaurantBasicInfo(
                        r.getId(),
                        r.getName(),
                        r.getAddress(),
                        r.getPhone(),
                        r.getLogoUrl()
                ))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    public record RestaurantBasicInfo(Long id, String name, String address, String phone, String logoUrl) {}

    @GetMapping("/public/restaurants/{restaurantId}/tables")
    @Operation(summary = "Get tables for reservation", description = "Get all tables with availability for reservation")
    public ResponseEntity<ApiResponse<List<TableAvailabilityResponse>>> getTablesForReservation(
            @PathVariable Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
            @RequestParam(defaultValue = "2") int partySize) {
        List<TableAvailabilityResponse> tables = tableAvailabilityService
                .getTablesWithAvailability(restaurantId, date, time, partySize);
        return ResponseEntity.ok(ApiResponse.success(tables));
    }

    @GetMapping("/public/restaurants/{restaurantId}/tables/sections")
    @Operation(summary = "Get table sections", description = "Get distinct table sections/areas")
    public ResponseEntity<ApiResponse<List<String>>> getTableSections(
            @PathVariable Long restaurantId) {
        List<String> sections = tableAvailabilityService.getTableSections(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(sections));
    }

    @PostMapping("/public/restaurants/{restaurantId}/reservations")
    @Operation(summary = "Create reservation", description = "Create a new table reservation (public)")
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservation(
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse reservation = reservationService.createReservation(restaurantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reservation created successfully", reservation));
    }

    @GetMapping("/public/reservations/{confirmationCode}")
    @Operation(summary = "Get reservation by code", description = "Get reservation details by confirmation code")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservationByCode(
            @PathVariable String confirmationCode) {
        ReservationResponse reservation = reservationService.getReservationByCode(confirmationCode);
        return ResponseEntity.ok(ApiResponse.success(reservation));
    }

    @GetMapping("/public/restaurants/{restaurantId}/availability")
    @Operation(summary = "Check availability", description = "Get available time slots for a date")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> checkAvailability(
            @PathVariable Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "2") int partySize) {
        AvailabilityResponse availability = availabilityService.getAvailability(restaurantId, date, partySize);
        return ResponseEntity.ok(ApiResponse.success(availability));
    }

    @GetMapping("/public/restaurants/{restaurantId}/availability/range")
    @Operation(summary = "Check availability range", description = "Get availability for a date range")
    public ResponseEntity<ApiResponse<List<AvailabilityResponse>>> checkAvailabilityRange(
            @PathVariable Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "2") int partySize) {
        List<AvailabilityResponse> availabilities = availabilityService
                .getAvailabilityRange(restaurantId, startDate, endDate, partySize);
        return ResponseEntity.ok(ApiResponse.success(availabilities));
    }

    @PostMapping("/public/reservations/{confirmationCode}/cancel")
    @Operation(summary = "Cancel reservation by code", description = "Cancel a reservation using confirmation code")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancelReservationByCode(
            @PathVariable String confirmationCode,
            @RequestParam(required = false) String reason) {
        ReservationResponse reservation = reservationService.getReservationByCode(confirmationCode);
        ReservationResponse cancelled = reservationService.cancelReservation(reservation.getId(), reason);
        return ResponseEntity.ok(ApiResponse.success("Reservation cancelled", cancelled));
    }

    @GetMapping("/public/reservations/phone/{phone}")
    @Operation(summary = "Get reservations by phone", description = "Get customer's reservations by phone number")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getReservationsByPhone(
            @PathVariable String phone) {
        List<ReservationResponse> reservations = reservationService.getReservationsByPhone(phone);
        return ResponseEntity.ok(ApiResponse.success(reservations));
    }

    // ========== Admin Endpoints ==========

    @GetMapping("/restaurants/{restaurantId}/reservations")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "List reservations", description = "Get all reservations for a restaurant")
    public ResponseEntity<ApiResponse<Page<ReservationResponse>>> getReservations(
            @PathVariable Long restaurantId,
            Pageable pageable) {
        Page<ReservationResponse> reservations = reservationService.getReservations(restaurantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(reservations));
    }

    @GetMapping("/restaurants/{restaurantId}/reservations/date/{date}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Get reservations by date", description = "Get reservations for a specific date")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getReservationsByDate(
            @PathVariable Long restaurantId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<ReservationResponse> reservations = reservationService.getReservationsByDate(restaurantId, date);
        return ResponseEntity.ok(ApiResponse.success(reservations));
    }

    @GetMapping("/restaurants/{restaurantId}/reservations/range")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Get reservations by date range", description = "Get reservations for calendar view")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getReservationsByDateRange(
            @PathVariable Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<ReservationResponse> reservations = reservationService
                .getReservationsByDateRange(restaurantId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(reservations));
    }

    @GetMapping("/reservations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Get reservation", description = "Get reservation by ID")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservation(@PathVariable Long id) {
        ReservationResponse reservation = reservationService.getReservation(id);
        return ResponseEntity.ok(ApiResponse.success(reservation));
    }

    @PostMapping("/restaurants/{restaurantId}/reservations")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Create reservation (admin)", description = "Create a new table reservation by admin/staff")
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservationAdmin(
            @PathVariable Long restaurantId,
            @RequestBody AdminCreateReservationRequest request) {
        // Convert admin request to standard request (without @Future validation)
        CreateReservationRequest createRequest = CreateReservationRequest.builder()
                .customerName(request.customerName())
                .customerPhone(request.customerPhone())
                .reservationDate(request.reservationDate())
                .reservationTime(request.reservationTime())
                .partySize(request.partySize())
                .tableId(request.tableId())
                .specialRequests(request.specialRequests())
                .source("ADMIN")
                .build();
        ReservationResponse reservation = reservationService.createReservation(restaurantId, createRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reservation created successfully", reservation));
    }

    public record AdminCreateReservationRequest(
            String customerName,
            String customerPhone,
            LocalDate reservationDate,
            LocalTime reservationTime,
            Integer partySize,
            Long tableId,
            String specialRequests
    ) {}

    @PostMapping("/reservations/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Confirm reservation", description = "Confirm a pending reservation")
    public ResponseEntity<ApiResponse<ReservationResponse>> confirmReservation(@PathVariable Long id) {
        ReservationResponse reservation = reservationService.confirmReservation(id, null);
        return ResponseEntity.ok(ApiResponse.success("Reservation confirmed", reservation));
    }

    @PostMapping("/reservations/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Cancel reservation", description = "Cancel a reservation")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancelReservation(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        ReservationResponse reservation = reservationService.cancelReservation(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Reservation cancelled", reservation));
    }

    @PostMapping("/reservations/{id}/check-in")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Check in guest", description = "Mark guest as arrived/seated")
    public ResponseEntity<ApiResponse<ReservationResponse>> checkIn(@PathVariable Long id) {
        ReservationResponse reservation = reservationService.checkIn(id);
        return ResponseEntity.ok(ApiResponse.success("Guest checked in", reservation));
    }

    @PostMapping("/reservations/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Complete reservation", description = "Mark reservation as completed")
    public ResponseEntity<ApiResponse<ReservationResponse>> completeReservation(@PathVariable Long id) {
        ReservationResponse reservation = reservationService.completeReservation(id);
        return ResponseEntity.ok(ApiResponse.success("Reservation completed", reservation));
    }

    @PostMapping("/reservations/{id}/no-show")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Mark no-show", description = "Mark customer as no-show")
    public ResponseEntity<ApiResponse<ReservationResponse>> markNoShow(@PathVariable Long id) {
        ReservationResponse reservation = reservationService.markNoShow(id);
        return ResponseEntity.ok(ApiResponse.success("Marked as no-show", reservation));
    }

    @PostMapping("/reservations/{id}/assign-table/{tableId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Assign table", description = "Assign a table to reservation")
    public ResponseEntity<ApiResponse<ReservationResponse>> assignTable(
            @PathVariable Long id,
            @PathVariable Long tableId) {
        ReservationResponse reservation = reservationService.assignTable(id, tableId);
        return ResponseEntity.ok(ApiResponse.success("Table assigned", reservation));
    }
}

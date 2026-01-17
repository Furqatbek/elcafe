package com.elcafe.modules.reservation.controller;

import com.elcafe.modules.reservation.dto.ReservationSettingsDTO;
import com.elcafe.modules.reservation.entity.ReservationSettings;
import com.elcafe.modules.reservation.repository.ReservationSettingsRepository;
import com.elcafe.modules.reservation.service.AvailabilityService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/reservation-settings")
@RequiredArgsConstructor
@Tag(name = "Reservation Settings", description = "Manage restaurant reservation settings")
public class ReservationSettingsController {

    private final ReservationSettingsRepository settingsRepository;
    private final AvailabilityService availabilityService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Get reservation settings", description = "Get reservation settings for a restaurant")
    public ResponseEntity<ApiResponse<ReservationSettingsDTO>> getSettings(
            @PathVariable Long restaurantId) {
        ReservationSettings settings = availabilityService.getOrCreateSettings(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(ReservationSettingsDTO.from(settings)));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Update reservation settings", description = "Update reservation settings for a restaurant")
    public ResponseEntity<ApiResponse<ReservationSettingsDTO>> updateSettings(
            @PathVariable Long restaurantId,
            @Valid @RequestBody ReservationSettingsDTO request) {

        ReservationSettings settings = availabilityService.getOrCreateSettings(restaurantId);

        // Update fields
        if (request.getEnabled() != null) {
            settings.setEnabled(request.getEnabled());
        }
        if (request.getAdvanceDays() != null) {
            settings.setAdvanceDays(request.getAdvanceDays());
        }
        if (request.getMinAdvanceHours() != null) {
            settings.setMinAdvanceHours(request.getMinAdvanceHours());
        }
        if (request.getSlotDurationMinutes() != null) {
            settings.setSlotDurationMinutes(request.getSlotDurationMinutes());
        }
        if (request.getMinPartySize() != null) {
            settings.setMinPartySize(request.getMinPartySize());
        }
        if (request.getMaxPartySize() != null) {
            settings.setMaxPartySize(request.getMaxPartySize());
        }
        if (request.getDepositRequired() != null) {
            settings.setDepositRequired(request.getDepositRequired());
        }
        if (request.getDepositAmount() != null) {
            settings.setDepositAmount(request.getDepositAmount());
        }
        if (request.getDepositPercent() != null) {
            settings.setDepositPercent(request.getDepositPercent());
        }
        if (request.getCancellationHours() != null) {
            settings.setCancellationHours(request.getCancellationHours());
        }
        if (request.getAutoConfirm() != null) {
            settings.setAutoConfirm(request.getAutoConfirm());
        }
        if (request.getSendReminders() != null) {
            settings.setSendReminders(request.getSendReminders());
        }
        if (request.getReminderHoursBefore() != null) {
            settings.setReminderHoursBefore(request.getReminderHoursBefore());
        }
        if (request.getMaxReservationsPerSlot() != null) {
            settings.setMaxReservationsPerSlot(request.getMaxReservationsPerSlot());
        }
        if (request.getNotesForCustomers() != null) {
            settings.setNotesForCustomers(request.getNotesForCustomers());
        }

        ReservationSettings saved = settingsRepository.save(settings);
        return ResponseEntity.ok(ApiResponse.success("Settings updated", ReservationSettingsDTO.from(saved)));
    }
}

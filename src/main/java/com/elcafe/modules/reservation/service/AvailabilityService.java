package com.elcafe.modules.reservation.service;

import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.reservation.dto.AvailabilityResponse;
import com.elcafe.modules.reservation.entity.Reservation;
import com.elcafe.modules.reservation.entity.ReservationSettings;
import com.elcafe.modules.reservation.enums.ReservationStatus;
import com.elcafe.modules.reservation.repository.ReservationRepository;
import com.elcafe.modules.reservation.repository.ReservationSettingsRepository;
import com.elcafe.modules.restaurant.entity.BusinessHours;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.BusinessHoursRepository;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final ReservationRepository reservationRepository;
    private final ReservationSettingsRepository settingsRepository;
    private final RestaurantRepository restaurantRepository;
    private final BusinessHoursRepository businessHoursRepository;
    private final ShiftTimeService shiftTimeService;

    /**
     * Check if a specific time slot is available
     */
    @Transactional
    public boolean isSlotAvailable(Long restaurantId, LocalDate date, LocalTime time, int partySize) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
        if (restaurant == null) {
            return false;
        }

        ReservationSettings settings = settingsRepository.findByRestaurantId(restaurantId)
                .orElseGet(() -> createDefaultSettings(restaurant));

        if (!settings.getEnabled()) {
            return false;
        }

        // Check if date is too far in advance
        LocalDate maxDate = LocalDate.now().plusDays(settings.getAdvanceDays());
        if (date.isAfter(maxDate)) {
            return false;
        }

        // Check party size
        if (partySize < settings.getMinPartySize() || partySize > settings.getMaxPartySize()) {
            return false;
        }

        // Check max reservations per slot (using overlap logic)
        if (settings.getMaxReservationsPerSlot() != null) {
            List<Reservation> activeReservations = reservationRepository.findByRestaurantIdAndReservationDate(restaurantId, date)
                    .stream()
                    .filter(r -> r.getStatus() != ReservationStatus.CANCELLED && r.getStatus() != ReservationStatus.NO_SHOW)
                    .toList();

            long currentCount = countOverlappingReservations(activeReservations, time, settings.getSlotDurationMinutes());
            if (currentCount >= settings.getMaxReservationsPerSlot()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get available time slots for a date
     */
    @Transactional
    public AvailabilityResponse getAvailability(Long restaurantId, LocalDate date, int partySize) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
        if (restaurant == null) {
            return AvailabilityResponse.builder()
                    .date(date)
                    .available(false)
                    .message("Restaurant not found")
                    .build();
        }

        // Get or create default reservation settings
        ReservationSettings settings = settingsRepository.findByRestaurantId(restaurantId)
                .orElseGet(() -> createDefaultSettings(restaurant));

        if (!settings.getEnabled()) {
            return AvailabilityResponse.builder()
                    .date(date)
                    .available(false)
                    .message("Online reservations are not available")
                    .build();
        }

        // Check date validity
        LocalDate today = LocalDate.now();
        LocalDate maxDate = today.plusDays(settings.getAdvanceDays());

        if (date.isBefore(today)) {
            return AvailabilityResponse.builder()
                    .date(date)
                    .available(false)
                    .message("Cannot book in the past")
                    .build();
        }

        if (date.isAfter(maxDate)) {
            return AvailabilityResponse.builder()
                    .date(date)
                    .available(false)
                    .message("Cannot book more than " + settings.getAdvanceDays() + " days in advance")
                    .build();
        }

        // Check party size
        if (partySize < settings.getMinPartySize() || partySize > settings.getMaxPartySize()) {
            return AvailabilityResponse.builder()
                    .date(date)
                    .available(false)
                    .message("Party size must be between " + settings.getMinPartySize() +
                            " and " + settings.getMaxPartySize())
                    .build();
        }

        // Use ShiftTimeService to get shift time range (handles midnight-crossing shifts)
        ShiftTimeService.ShiftTimeRange shiftRange = shiftTimeService.getShiftTimeRange(restaurantId, date);

        // Check if restaurant is closed (full day range means no business hours or closed)
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        Optional<BusinessHours> businessHoursOpt = businessHoursRepository
                .findByRestaurant_IdAndDayOfWeek(restaurantId, dayOfWeek);

        if (businessHoursOpt.isPresent() && businessHoursOpt.get().getClosed()) {
            return AvailabilityResponse.builder()
                    .date(date)
                    .available(false)
                    .message("Restaurant is closed on " + dayOfWeek.toString().toLowerCase())
                    .build();
        }

        LocalTime openTime = shiftRange.openTime();
        LocalTime closeTime = shiftRange.closeTime();
        LocalDateTime shiftStart = shiftRange.start();
        LocalDateTime shiftEnd = shiftRange.end();

        // Check if shift crosses midnight
        boolean crossesMidnight = closeTime.isBefore(openTime) || closeTime.equals(openTime);

        int slotDuration = settings.getSlotDurationMinutes();
        List<AvailabilityResponse.TimeSlot> timeSlots = new ArrayList<>();

        // Use LocalDateTime for proper handling of midnight-crossing shifts
        LocalDateTime currentSlotTime = shiftStart;
        LocalDateTime minAdvanceTime = LocalDateTime.now().plusHours(settings.getMinAdvanceHours());

        // For reservation purposes, don't extend to next day's opening time
        // Instead, use actual closing time (either same day or next day if crosses midnight)
        LocalDateTime reservationEndTime;
        if (crossesMidnight) {
            // Closing time is on the next day
            reservationEndTime = date.plusDays(1).atTime(closeTime);
        } else {
            reservationEndTime = date.atTime(closeTime);
        }

        log.debug("Generating time slots for restaurant {} on {}: {} to {} (crosses midnight: {})",
                restaurantId, date, shiftStart, reservationEndTime, crossesMidnight);

        // Fetch all active reservations for the date once (more efficient than querying per slot)
        List<Reservation> activeReservations = reservationRepository.findByRestaurantIdAndReservationDate(restaurantId, date)
                .stream()
                .filter(r -> r.getStatus() != ReservationStatus.CANCELLED && r.getStatus() != ReservationStatus.NO_SHOW)
                .toList();

        while (currentSlotTime.plusMinutes(slotDuration).isBefore(reservationEndTime) ||
               currentSlotTime.plusMinutes(slotDuration).equals(reservationEndTime)) {

            LocalTime slotTime = currentSlotTime.toLocalTime();

            // Skip past times (slots that are before minimum advance time)
            if (currentSlotTime.isBefore(minAdvanceTime)) {
                currentSlotTime = currentSlotTime.plusMinutes(slotDuration);
                continue;
            }

            int maxSpots = settings.getMaxReservationsPerSlot() != null ?
                    settings.getMaxReservationsPerSlot() : 10;

            // Count overlapping reservations (not just exact time matches)
            long currentCount = countOverlappingReservations(activeReservations, slotTime, slotDuration);
            int availableSpots = maxSpots - (int) currentCount;
            boolean slotAvailable = availableSpots > 0;

            timeSlots.add(AvailabilityResponse.TimeSlot.builder()
                    .time(slotTime)
                    .available(slotAvailable)
                    .availableSpots(Math.max(0, availableSpots))
                    .maxSpots(maxSpots)
                    .build());

            currentSlotTime = currentSlotTime.plusMinutes(slotDuration);
        }

        log.debug("Generated {} time slots for restaurant {} on {}", timeSlots.size(), restaurantId, date);

        boolean anyAvailable = timeSlots.stream().anyMatch(AvailabilityResponse.TimeSlot::isAvailable);

        return AvailabilityResponse.builder()
                .date(date)
                .available(anyAvailable)
                .timeSlots(timeSlots)
                .build();
    }

    /**
     * Get availability for multiple dates (for calendar view)
     */
    @Transactional(readOnly = true)
    public List<AvailabilityResponse> getAvailabilityRange(
            Long restaurantId, LocalDate startDate, LocalDate endDate, int partySize) {

        List<AvailabilityResponse> availabilities = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            availabilities.add(getAvailability(restaurantId, current, partySize));
            current = current.plusDays(1);
        }

        return availabilities;
    }

    /**
     * Create default reservation settings for a restaurant
     */
    private ReservationSettings createDefaultSettings(Restaurant restaurant) {
        log.info("Creating default reservation settings for restaurant: {}", restaurant.getId());

        ReservationSettings settings = ReservationSettings.builder()
                .restaurant(restaurant)
                .enabled(true)
                .advanceDays(30)
                .minAdvanceHours(2)
                .slotDurationMinutes(60)
                .minPartySize(1)
                .maxPartySize(12)
                .depositRequired(false)
                .autoConfirm(true)
                .sendReminders(true)
                .reminderHoursBefore(24)
                .cancellationHours(24)
                .maxReservationsPerSlot(10)
                .build();

        return settingsRepository.save(settings);
    }

    /**
     * Get or create settings for a restaurant
     */
    @Transactional
    public ReservationSettings getOrCreateSettings(Long restaurantId) {
        return settingsRepository.findByRestaurantId(restaurantId)
                .orElseGet(() -> {
                    Restaurant restaurant = restaurantRepository.findById(restaurantId)
                            .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));
                    return createDefaultSettings(restaurant);
                });
    }

    /**
     * Count reservations that overlap with a given time slot.
     * Two time intervals overlap if: start1 < end2 AND start2 < end1
     */
    private long countOverlappingReservations(List<Reservation> reservations, LocalTime slotStart, int slotDurationMinutes) {
        LocalTime slotEnd = slotStart.plusMinutes(slotDurationMinutes);

        return reservations.stream()
                .filter(r -> {
                    LocalTime resStart = r.getReservationTime();
                    LocalTime resEnd = resStart.plusMinutes(r.getDurationMinutes());

                    // Check for overlap: slotStart < resEnd AND resStart < slotEnd
                    return slotStart.isBefore(resEnd) && resStart.isBefore(slotEnd);
                })
                .count();
    }
}

package com.elcafe.modules.reservation.service;

import com.elcafe.modules.reservation.dto.AvailabilityResponse;
import com.elcafe.modules.reservation.entity.ReservationSettings;
import com.elcafe.modules.reservation.enums.ReservationStatus;
import com.elcafe.modules.reservation.repository.ReservationRepository;
import com.elcafe.modules.reservation.repository.ReservationSettingsRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final ReservationRepository reservationRepository;
    private final ReservationSettingsRepository settingsRepository;
    private final RestaurantRepository restaurantRepository;

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

        // Check max reservations per slot
        if (settings.getMaxReservationsPerSlot() != null) {
            long currentCount = reservationRepository.countReservationsAtSlot(restaurantId, date, time);
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

        // Generate time slots based on restaurant hours
        // Using default hours 10:00-22:00 for simplicity
        // In production, this should be based on restaurant operating hours
        LocalTime openTime = LocalTime.of(10, 0);
        LocalTime closeTime = LocalTime.of(22, 0);
        int slotDuration = settings.getSlotDurationMinutes();

        List<AvailabilityResponse.TimeSlot> timeSlots = new ArrayList<>();
        LocalTime currentTime = openTime;

        while (currentTime.plusMinutes(slotDuration).isBefore(closeTime) ||
               currentTime.plusMinutes(slotDuration).equals(closeTime)) {

            // Skip past times for today
            if (date.equals(today)) {
                LocalTime minTime = LocalTime.now().plusHours(settings.getMinAdvanceHours());
                if (currentTime.isBefore(minTime)) {
                    currentTime = currentTime.plusMinutes(slotDuration);
                    continue;
                }
            }

            int maxSpots = settings.getMaxReservationsPerSlot() != null ?
                    settings.getMaxReservationsPerSlot() : 10;

            long currentCount = reservationRepository.countReservationsAtSlot(restaurantId, date, currentTime);
            int availableSpots = maxSpots - (int) currentCount;
            boolean slotAvailable = availableSpots > 0;

            timeSlots.add(AvailabilityResponse.TimeSlot.builder()
                    .time(currentTime)
                    .available(slotAvailable)
                    .availableSpots(Math.max(0, availableSpots))
                    .maxSpots(maxSpots)
                    .build());

            currentTime = currentTime.plusMinutes(slotDuration);
        }

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
}

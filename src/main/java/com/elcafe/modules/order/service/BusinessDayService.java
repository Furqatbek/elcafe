package com.elcafe.modules.order.service;

import com.elcafe.modules.restaurant.entity.WorkingHours;
import com.elcafe.modules.restaurant.repository.WorkingHoursRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for calculating business day boundaries based on restaurant working hours.
 *
 * A business day is defined by the restaurant's working hours, not calendar days.
 * For example, if a restaurant works from 18:00 to 04:00, orders from 18:00 on Monday
 * to 04:00 on Tuesday are considered part of Monday's business day.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessDayService {

    private final WorkingHoursRepository workingHoursRepository;

    // Default working hours if restaurant doesn't have configured hours
    private static final LocalTime DEFAULT_START_TIME = LocalTime.of(10, 0);
    private static final LocalTime DEFAULT_END_TIME = LocalTime.of(23, 0);

    /**
     * Get business day boundaries for a given date and restaurant.
     * Returns a DateRange with the actual start and end times considering working hours.
     */
    public DateRange getBusinessDayRange(Long restaurantId, LocalDate date) {
        WorkingHoursInfo workingHours = getWorkingHoursForDate(restaurantId, date);

        LocalTime startTime = workingHours.startTime();
        LocalTime endTime = workingHours.endTime();

        LocalDateTime businessDayStart = date.atTime(startTime);
        LocalDateTime businessDayEnd;

        // If end time is before start time, it means the business day spans midnight
        // e.g., 18:00 to 04:00 means the business day ends at 04:00 the next calendar day
        if (endTime.isBefore(startTime) || endTime.equals(LocalTime.MIDNIGHT)) {
            businessDayEnd = date.plusDays(1).atTime(endTime);
        } else {
            businessDayEnd = date.atTime(endTime);
        }

        log.debug("Business day range for restaurant {} on {}: {} to {}",
                restaurantId, date, businessDayStart, businessDayEnd);

        return new DateRange(businessDayStart, businessDayEnd);
    }

    /**
     * Get business day boundaries for a date range.
     * The fromDate uses the business day start, and toDate uses the business day end.
     */
    public DateRange getBusinessDayRangeForPeriod(Long restaurantId, LocalDate fromDate, LocalDate toDate) {
        // Get the start time from the first day
        DateRange fromDayRange = getBusinessDayRange(restaurantId, fromDate);
        // Get the end time from the last day
        DateRange toDayRange = getBusinessDayRange(restaurantId, toDate);

        return new DateRange(fromDayRange.from(), toDayRange.to());
    }

    /**
     * Adjust filter dates to capture all orders for a calendar date plus its business day.
     *
     * For example, with business hours 18:00-04:00, filtering for "January 6th" will include:
     * - Orders from 00:00-04:00 on Jan 6 (early morning, part of Jan 5's business day but created on Jan 6)
     * - Orders from 04:00-18:00 on Jan 6 (gap time between business days)
     * - Orders from 18:00-23:59 on Jan 6 (start of Jan 6's business day)
     * - Orders from 00:00-04:00 on Jan 7 (continuation of Jan 6's business day)
     *
     * This ensures ALL orders created on the calendar date are included, plus orders that
     * belong to that date's business day which may extend into the next calendar day.
     */
    public DateRange adjustToBusinessDayBoundaries(Long restaurantId, LocalDateTime fromDate, LocalDateTime toDate) {
        LocalDateTime adjustedFrom = fromDate;
        LocalDateTime adjustedTo = toDate;

        if (fromDate != null) {
            // Start from the beginning of the calendar day (00:00)
            // This captures all orders created on this date, including early morning hours
            adjustedFrom = fromDate.toLocalDate().atStartOfDay();
        }

        if (toDate != null) {
            // End at the business day end, which may be on the next calendar day
            DateRange toDayRange = getBusinessDayRange(restaurantId, toDate.toLocalDate());
            adjustedTo = toDayRange.to();
        }

        log.info("Adjusted date range to include full calendar day + business day end: {} to {} -> {} to {}",
                fromDate, toDate, adjustedFrom, adjustedTo);

        return new DateRange(adjustedFrom, adjustedTo);
    }

    /**
     * Get working hours for a specific date and restaurant.
     * Falls back to default hours if not configured.
     */
    private WorkingHoursInfo getWorkingHoursForDate(Long restaurantId, LocalDate date) {
        if (restaurantId == null) {
            return new WorkingHoursInfo(DEFAULT_START_TIME, DEFAULT_END_TIME);
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        List<WorkingHours> hoursList = workingHoursRepository.findByRestaurant_IdAndDayOfWeek(restaurantId, dayOfWeek);

        // Get the first active working hours entry for this day
        Optional<WorkingHours> workingHours = hoursList.stream()
                .filter(wh -> wh.getActive() != null && wh.getActive())
                .findFirst();

        if (workingHours.isPresent()) {
            WorkingHours wh = workingHours.get();
            return new WorkingHoursInfo(wh.getStartTime(), wh.getEndTime());
        }

        // Fallback: try to get any working hours for this restaurant to use as default
        List<WorkingHours> allHours = workingHoursRepository.findByRestaurant_Id(restaurantId);
        if (!allHours.isEmpty()) {
            // Use the first entry's times as default
            WorkingHours wh = allHours.get(0);
            log.debug("Using fallback working hours for restaurant {} on {}: {} to {}",
                    restaurantId, dayOfWeek, wh.getStartTime(), wh.getEndTime());
            return new WorkingHoursInfo(wh.getStartTime(), wh.getEndTime());
        }

        log.debug("No working hours found for restaurant {} on {}, using defaults", restaurantId, dayOfWeek);
        return new WorkingHoursInfo(DEFAULT_START_TIME, DEFAULT_END_TIME);
    }

    /**
     * Record to hold working hours start and end time.
     */
    private record WorkingHoursInfo(LocalTime startTime, LocalTime endTime) {}

    /**
     * Record to hold a date range with from and to timestamps.
     */
    public record DateRange(LocalDateTime from, LocalDateTime to) {}
}

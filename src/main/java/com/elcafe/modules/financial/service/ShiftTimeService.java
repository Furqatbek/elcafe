package com.elcafe.modules.financial.service;

import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.restaurant.entity.BusinessHours;
import com.elcafe.modules.restaurant.repository.BusinessHoursRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Shared service for calculating shift time ranges based on business hours.
 * Used by all financial reporting services to ensure consistent date/time handling.
 *
 * This service handles shifts that cross midnight (e.g., opens 11:00, closes 02:00 next day).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftTimeService {

    private final BusinessHoursRepository businessHoursRepository;

    /**
     * Order statuses that count as revenue/income.
     * All confirmed orders are included (orders that have been accepted by the restaurant).
     * Excluded: PENDING, NEW, PLACED, REJECTED, CANCELLED
     */
    public static final Set<OrderStatus> REVENUE_STATUSES = Set.of(
            OrderStatus.ACCEPTED,
            OrderStatus.PREPARING,
            OrderStatus.READY,
            OrderStatus.PICKED_UP,
            OrderStatus.COURIER_ASSIGNED,
            OrderStatus.ON_DELIVERY,
            OrderStatus.DELIVERED,
            OrderStatus.COMPLETED
    );

    /**
     * Record class for shift time range with start and end datetime
     */
    public record ShiftTimeRange(
        LocalDateTime start,
        LocalDateTime end,
        LocalTime openTime,
        LocalTime closeTime
    ) {}

    /**
     * Get shift time range for a restaurant on a specific date.
     * Uses business hours to determine shift start and end times.
     * If shift crosses midnight (e.g., opens 10:00, closes 02:00), end time is next day.
     *
     * IMPORTANT: To avoid gaps between shifts, the shift end time is extended to the
     * opening time of the next shift. This ensures orders created during the gap period
     * (e.g., 02:00 AM - 11:00 AM) are included in the previous shift's reports.
     *
     * @param restaurantId The restaurant ID (can be null, will use full calendar day)
     * @param date The business date (shift starts on this date)
     * @return ShiftTimeRange with start and end LocalDateTime
     */
    public ShiftTimeRange getShiftTimeRange(Long restaurantId, LocalDate date) {
        // Handle null restaurantId - use full calendar day as fallback
        if (restaurantId == null) {
            log.debug("No restaurantId provided for date {}, using full calendar day", date);
            return new ShiftTimeRange(
                date.atStartOfDay(),
                date.atTime(23, 59, 59),
                LocalTime.of(0, 0),
                LocalTime.of(23, 59)
            );
        }

        var businessHours = businessHoursRepository.findByRestaurant_IdAndDayOfWeek(
            restaurantId, date.getDayOfWeek());

        if (businessHours.isEmpty() || businessHours.get().getClosed()) {
            // If no business hours or closed, use full calendar day as fallback
            log.debug("No business hours found for restaurant {} on {}, using full day", restaurantId, date);
            return new ShiftTimeRange(
                date.atStartOfDay(),
                date.atTime(23, 59, 59),
                LocalTime.of(0, 0),
                LocalTime.of(23, 59)
            );
        }

        BusinessHours hours = businessHours.get();
        LocalTime openTime = hours.getOpenTime();
        LocalTime closeTime = hours.getCloseTime();

        LocalDateTime shiftStart = date.atTime(openTime);
        LocalDateTime shiftEnd;

        // Always extend end time to next day's opening time
        // This ensures ALL orders for this business day are included:
        // - Orders during operating hours (09:00-22:00)
        // - Orders after closing but before next day's opening (22:00-09:00)
        // This prevents gaps where orders would be missed
        shiftEnd = date.plusDays(1).atTime(openTime);

        log.debug("Shift for restaurant {} on {}: {} to {} (business hours: {}-{})",
            restaurantId, date, shiftStart, shiftEnd, openTime, closeTime);

        return new ShiftTimeRange(shiftStart, shiftEnd, openTime, closeTime);
    }

    /**
     * Get combined shift time range for a date range.
     * For single day: uses shift time range based on business hours.
     * For multiple days: uses shift start of first day to shift end of last day.
     *
     * @param restaurantId The restaurant ID
     * @param startDate The start date
     * @param endDate The end date
     * @return ShiftTimeRange covering the entire period
     */
    public ShiftTimeRange getShiftTimeRangeForPeriod(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        if (startDate.equals(endDate)) {
            // Single day - use shift time range based on business hours
            return getShiftTimeRange(restaurantId, startDate);
        } else {
            // Multi-day - use shift start of first day to shift end of last day
            ShiftTimeRange firstShift = getShiftTimeRange(restaurantId, startDate);
            ShiftTimeRange lastShift = getShiftTimeRange(restaurantId, endDate);

            log.debug("Multi-day shift range for restaurant {} from {} to {}: {} to {}",
                restaurantId, startDate, endDate, firstShift.start(), lastShift.end());

            return new ShiftTimeRange(
                firstShift.start(),
                lastShift.end(),
                firstShift.openTime(),
                lastShift.closeTime()
            );
        }
    }

    /**
     * Get the current business day for a restaurant based on business hours.
     *
     * For shifts that cross midnight (e.g., 21:00-03:00):
     * - If current time is BEFORE opening time (e.g., 04:00 AM), we're still in yesterday's business day
     * - If current time is AFTER opening time (e.g., 22:00), we're in today's business day
     *
     * Example: For 21:00-03:00 shift at 04:00 AM on Jan 7:
     * - Returns Jan 6 (because Jan 6's shift runs from 21:00 Jan 6 to 21:00 Jan 7)
     *
     * @param restaurantId The restaurant ID
     * @return The current business day date
     */
    public LocalDate getCurrentBusinessDay(Long restaurantId) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        if (restaurantId == null) {
            return today;
        }

        var businessHours = businessHoursRepository.findByRestaurant_IdAndDayOfWeek(
            restaurantId, today.getDayOfWeek());

        if (businessHours.isEmpty() || businessHours.get().getClosed()) {
            // No business hours, use calendar day
            return today;
        }

        LocalTime openTime = businessHours.get().getOpenTime();
        LocalTime closeTime = businessHours.get().getCloseTime();

        // Check if shift crosses midnight
        boolean crossesMidnight = closeTime.isBefore(openTime) || closeTime.equals(openTime);

        if (crossesMidnight && now.isBefore(openTime)) {
            // We're in the early morning hours before today's shift starts
            // This means we're still in yesterday's business day
            log.debug("Current time {} is before opening {}, using yesterday as business day", now, openTime);
            return today.minusDays(1);
        }

        return today;
    }

    /**
     * Check if an order status counts as revenue/income.
     *
     * @param status The order status to check
     * @return true if the status counts as revenue
     */
    public boolean isRevenueStatus(OrderStatus status) {
        return REVENUE_STATUSES.contains(status);
    }

    /**
     * Get the list of revenue statuses as a List (for stream operations).
     *
     * @return List of order statuses that count as revenue
     */
    public List<OrderStatus> getRevenueStatusList() {
        return List.copyOf(REVENUE_STATUSES);
    }
}

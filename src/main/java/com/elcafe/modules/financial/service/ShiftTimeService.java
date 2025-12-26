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
     * @param restaurantId The restaurant ID
     * @param date The business date (shift starts on this date)
     * @return ShiftTimeRange with start and end LocalDateTime
     */
    public ShiftTimeRange getShiftTimeRange(Long restaurantId, LocalDate date) {
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

        // Check if shift crosses midnight (closeTime is before openTime)
        if (closeTime.isBefore(openTime) || closeTime.equals(openTime)) {
            // Shift ends next day
            shiftEnd = date.plusDays(1).atTime(closeTime);
            log.debug("Shift crosses midnight for restaurant {} on {}: {} to {}",
                restaurantId, date, shiftStart, shiftEnd);
        } else {
            // Shift ends same day
            shiftEnd = date.atTime(closeTime);
            log.debug("Normal shift for restaurant {} on {}: {} to {}",
                restaurantId, date, shiftStart, shiftEnd);
        }

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

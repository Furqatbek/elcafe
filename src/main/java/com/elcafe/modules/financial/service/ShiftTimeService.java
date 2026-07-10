package com.elcafe.modules.financial.service;

import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.restaurant.entity.BusinessHours;
import com.elcafe.modules.restaurant.repository.BusinessHoursRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
     * Record class for shift time range with start and end datetime.
     * Uses OffsetDateTime to match PostgreSQL TIMESTAMP WITH TIME ZONE columns.
     */
    public record ShiftTimeRange(
        OffsetDateTime start,
        OffsetDateTime end,
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
        ZoneId zoneId = ZoneId.systemDefault();

        // Handle null restaurantId - use full calendar day as fallback
        if (restaurantId == null) {
            log.debug("No restaurantId provided for date {}, using full calendar day", date);
            return new ShiftTimeRange(
                date.atStartOfDay().atZone(zoneId).toOffsetDateTime(),
                date.atTime(23, 59, 59).atZone(zoneId).toOffsetDateTime(),
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
                date.atStartOfDay().atZone(zoneId).toOffsetDateTime(),
                date.atTime(23, 59, 59).atZone(zoneId).toOffsetDateTime(),
                LocalTime.of(0, 0),
                LocalTime.of(23, 59)
            );
        }

        BusinessHours hours = businessHours.get();
        LocalTime openTime = hours.getOpenTime();
        LocalTime closeTime = hours.getCloseTime();

        OffsetDateTime shiftStart = date.atTime(openTime).atZone(zoneId).toOffsetDateTime();
        OffsetDateTime shiftEnd;

        // Check if shift crosses midnight (closeTime is before openTime)
        if (closeTime.isBefore(openTime) || closeTime.equals(openTime)) {
            // Shift crosses midnight - extend end time to next day's opening time
            // This ensures no gap between shifts (e.g., 02:00 AM to 11:00 AM gap is covered)
            shiftEnd = date.plusDays(1).atTime(openTime).atZone(zoneId).toOffsetDateTime();
            log.debug("Shift crosses midnight for restaurant {} on {}: {} to {} (extended to next opening)",
                restaurantId, date, shiftStart, shiftEnd);
        } else {
            // Normal shift - ends same day at closing time
            shiftEnd = date.atTime(closeTime).atZone(zoneId).toOffsetDateTime();
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
     * Get the current business day for a restaurant based on business hours.
     *
     * For shifts that cross midnight (e.g., 21:00-03:00):
     * - If current time is in the "after midnight" portion of yesterday's shift, return yesterday
     * - Otherwise, return today
     *
     * Example: For 21:00-03:00 shift at 01:00 AM on Jan 7:
     * - Yesterday (Jan 6) had shift 21:00-03:00
     * - Current time 01:00 is before close time 03:00
     * - Returns Jan 6 (because we're still in Jan 6's shift)
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

        // First, check if we're in the "after midnight" portion of YESTERDAY's shift
        // This is the key fix: we must check yesterday's business hours, not today's
        LocalDate yesterday = today.minusDays(1);
        var yesterdayHours = businessHoursRepository.findByRestaurant_IdAndDayOfWeek(
            restaurantId, yesterday.getDayOfWeek());

        if (yesterdayHours.isPresent() && !yesterdayHours.get().getClosed()) {
            LocalTime yesterdayOpen = yesterdayHours.get().getOpenTime();
            LocalTime yesterdayClose = yesterdayHours.get().getCloseTime();

            // Check if yesterday's shift crosses midnight
            boolean yesterdayCrossesMidnight = yesterdayClose.isBefore(yesterdayOpen)
                || yesterdayClose.equals(yesterdayOpen);

            if (yesterdayCrossesMidnight && now.isBefore(yesterdayClose)) {
                // We're in the early morning hours, still part of yesterday's shift
                // (e.g., at 01:00 AM and yesterday's shift ends at 03:00 AM)
                log.debug("Current time {} is before yesterday's close time {}, using yesterday as business day",
                    now, yesterdayClose);
                return yesterday;
            }
        }

        // Check today's business hours for the case where today's shift hasn't started yet
        // but yesterday's shift didn't cross midnight (gap between shifts)
        var todayHours = businessHoursRepository.findByRestaurant_IdAndDayOfWeek(
            restaurantId, today.getDayOfWeek());

        if (todayHours.isPresent() && !todayHours.get().getClosed()) {
            LocalTime todayOpen = todayHours.get().getOpenTime();
            LocalTime todayClose = todayHours.get().getCloseTime();

            // Check if today's shift crosses midnight and we're before opening
            boolean todayCrossesMidnight = todayClose.isBefore(todayOpen)
                || todayClose.equals(todayOpen);

            if (todayCrossesMidnight && now.isBefore(todayOpen)) {
                // Today's shift hasn't started yet and crosses midnight
                // We're in a gap period - attribute to yesterday for continuity
                log.debug("Current time {} is before today's opening {}, using yesterday as business day",
                    now, todayOpen);
                return yesterday;
            }
        }

        return today;
    }

    /**
     * Determine the business day that a given timestamp belongs to.
     *
     * For shifts that cross midnight (e.g., 10PM-3AM):
     * - A timestamp at 11PM Jan 5 belongs to Jan 5's business day
     * - A timestamp at 1AM Jan 6 belongs to Jan 5's business day (still in Jan 5's shift)
     *
     * For normal shifts (e.g., 9AM-10PM):
     * - A timestamp at any hour on Jan 5 belongs to Jan 5's business day
     *
     * This method is essential for correctly grouping orders by business day
     * in daily analytics reports for restaurants with midnight-crossing shifts.
     *
     * @param restaurantId The restaurant ID (can be null, will use calendar date)
     * @param timestamp The timestamp to determine the business day for
     * @return The business day (LocalDate) that the timestamp belongs to
     */
    public LocalDate getBusinessDay(Long restaurantId, LocalDateTime timestamp) {
        if (restaurantId == null) {
            return timestamp.toLocalDate();
        }
        return computeBusinessDay(timestamp,
                dayOfWeek -> businessHoursRepository.findByRestaurant_IdAndDayOfWeek(restaurantId, dayOfWeek));
    }

    /**
     * Batch-friendly variant of {@link #getBusinessDay}: loads the restaurant's business hours ONCE and
     * returns a resolver that computes business days purely in memory. {@code getBusinessDay} issues up
     * to two business-hours queries per call, which turns per-order grouping in analytics into an N+1 —
     * use this whenever attributing many timestamps (audit PERF-2).
     */
    public BusinessDayResolver businessDayResolver(Long restaurantId) {
        if (restaurantId == null) {
            return new BusinessDayResolver(null);
        }
        Map<DayOfWeek, BusinessHours> hoursByDay = businessHoursRepository.findByRestaurant_Id(restaurantId)
                .stream()
                .collect(Collectors.toMap(BusinessHours::getDayOfWeek, hours -> hours, (first, dup) -> first));
        return new BusinessDayResolver(hoursByDay);
    }

    /** In-memory business-day computer; obtain via {@link #businessDayResolver(Long)}. */
    public static final class BusinessDayResolver {
        private final Map<DayOfWeek, BusinessHours> hoursByDay;

        private BusinessDayResolver(Map<DayOfWeek, BusinessHours> hoursByDay) {
            this.hoursByDay = hoursByDay;
        }

        /** Same contract as {@code getBusinessDay}: null restaurant (no hours map) → calendar date. */
        public LocalDate businessDayFor(LocalDateTime timestamp) {
            if (hoursByDay == null) {
                return timestamp.toLocalDate();
            }
            return computeBusinessDay(timestamp, dayOfWeek -> Optional.ofNullable(hoursByDay.get(dayOfWeek)));
        }
    }

    /**
     * Core business-day attribution, shared by the per-call and batch paths so they cannot drift:
     * a timestamp before today's opening time, on a day whose previous day's shift crosses midnight,
     * belongs to the previous business day.
     */
    private static LocalDate computeBusinessDay(LocalDateTime timestamp,
                                                Function<DayOfWeek, Optional<BusinessHours>> hoursLookup) {
        LocalDate timestampDate = timestamp.toLocalDate();
        LocalTime timestampTime = timestamp.toLocalTime();

        // Check if the timestamp falls in the "after midnight" portion of yesterday's shift
        LocalDate yesterday = timestampDate.minusDays(1);
        var yesterdayHours = hoursLookup.apply(yesterday.getDayOfWeek());

        if (yesterdayHours.isPresent() && !yesterdayHours.get().getClosed()) {
            LocalTime yesterdayOpen = yesterdayHours.get().getOpenTime();
            LocalTime yesterdayClose = yesterdayHours.get().getCloseTime();

            // Check if yesterday's shift crosses midnight
            boolean yesterdayCrossesMidnight = yesterdayClose.isBefore(yesterdayOpen)
                || yesterdayClose.equals(yesterdayOpen);

            if (yesterdayCrossesMidnight) {
                // Yesterday's shift crosses midnight - check if timestamp is in the "after midnight" portion
                // The shift from yesterday extends until today's opening time
                var todayHours = hoursLookup.apply(timestampDate.getDayOfWeek());

                LocalTime todayOpen = todayHours.isPresent() && !todayHours.get().getClosed()
                    ? todayHours.get().getOpenTime()
                    : yesterdayOpen; // fallback to same opening time

                // If timestamp is before today's opening time, it belongs to yesterday's business day
                if (timestampTime.isBefore(todayOpen)) {
                    log.trace("Timestamp {} belongs to yesterday's business day {} (before today's opening {})",
                        timestamp, yesterday, todayOpen);
                    return yesterday;
                }
            }
        }

        // Default: timestamp belongs to its calendar date
        return timestampDate;
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

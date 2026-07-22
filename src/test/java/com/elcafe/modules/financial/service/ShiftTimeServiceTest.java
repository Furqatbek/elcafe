package com.elcafe.modules.financial.service;

import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.restaurant.entity.BusinessHours;
import com.elcafe.modules.restaurant.repository.BusinessHoursRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftTimeServiceTest {

    @Mock
    private BusinessHoursRepository businessHoursRepository;

    @InjectMocks
    private ShiftTimeService shiftTimeService;

    private static final Long RESTAURANT_ID = 1L;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private BusinessHours buildHours(DayOfWeek day, LocalTime open, LocalTime close, boolean closed) {
        return BusinessHours.builder()
                .id(1L)
                .dayOfWeek(day)
                .openTime(open)
                .closeTime(close)
                .closed(closed)
                .build();
    }

    // ---------------------------------------------------------------
    // 1. getShiftTimeRange - normal shift (same-day close)
    // ---------------------------------------------------------------
    @Test
    @DisplayName("getShiftTimeRange: normal shift 09:00-23:00 returns same-day range")
    void getShiftTimeRange_normalShift_9to23() {
        LocalDate date = LocalDate.of(2026, 3, 25); // Wednesday
        BusinessHours hours = buildHours(DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0), LocalTime.of(23, 0), false);

        when(businessHoursRepository.findByRestaurant_IdAndDayOfWeek(eq(RESTAURANT_ID), eq(DayOfWeek.WEDNESDAY)))
                .thenReturn(Optional.of(hours));

        ShiftTimeService.ShiftTimeRange range = shiftTimeService.getShiftTimeRange(RESTAURANT_ID, date);

        assertNotNull(range);

        OffsetDateTime expectedStart = date.atTime(9, 0).atZone(ZONE).toOffsetDateTime();
        OffsetDateTime expectedEnd = date.atTime(23, 0).atZone(ZONE).toOffsetDateTime();

        assertEquals(expectedStart, range.start());
        assertEquals(expectedEnd, range.end());
        assertEquals(LocalTime.of(9, 0), range.openTime());
        assertEquals(LocalTime.of(23, 0), range.closeTime());
    }

    // ---------------------------------------------------------------
    // 2. getShiftTimeRange - cross-midnight shift
    // ---------------------------------------------------------------
    @Test
    @DisplayName("getShiftTimeRange: cross-midnight shift 10:00-02:00 extends end to next day opening")
    void getShiftTimeRange_crossMidnightShift() {
        LocalDate date = LocalDate.of(2026, 3, 26); // Thursday
        BusinessHours hours = buildHours(DayOfWeek.THURSDAY,
                LocalTime.of(10, 0), LocalTime.of(2, 0), false);

        when(businessHoursRepository.findByRestaurant_IdAndDayOfWeek(eq(RESTAURANT_ID), eq(DayOfWeek.THURSDAY)))
                .thenReturn(Optional.of(hours));

        ShiftTimeService.ShiftTimeRange range = shiftTimeService.getShiftTimeRange(RESTAURANT_ID, date);

        assertNotNull(range);

        // Shift starts at 10:00 on Thursday
        OffsetDateTime expectedStart = date.atTime(10, 0).atZone(ZONE).toOffsetDateTime();
        // Cross-midnight: end is extended to next day's opening time (same openTime = 10:00 next day)
        OffsetDateTime expectedEnd = date.plusDays(1).atTime(10, 0).atZone(ZONE).toOffsetDateTime();

        assertEquals(expectedStart, range.start());
        assertEquals(expectedEnd, range.end());
        assertEquals(LocalTime.of(10, 0), range.openTime());
        assertEquals(LocalTime.of(2, 0), range.closeTime());
    }

    // ---------------------------------------------------------------
    // 3. getShiftTimeRange - no business hours, uses defaults
    // ---------------------------------------------------------------
    @Test
    @DisplayName("getShiftTimeRange: no business hours configured uses full calendar day fallback")
    void getShiftTimeRange_noBusinessHours_usesDefaults() {
        LocalDate date = LocalDate.of(2026, 3, 27); // Friday

        when(businessHoursRepository.findByRestaurant_IdAndDayOfWeek(eq(RESTAURANT_ID), eq(DayOfWeek.FRIDAY)))
                .thenReturn(Optional.empty());

        ShiftTimeService.ShiftTimeRange range = shiftTimeService.getShiftTimeRange(RESTAURANT_ID, date);

        assertNotNull(range);

        OffsetDateTime expectedStart = date.atStartOfDay().atZone(ZONE).toOffsetDateTime();
        OffsetDateTime expectedEnd = date.atTime(23, 59, 59).atZone(ZONE).toOffsetDateTime();

        assertEquals(expectedStart, range.start());
        assertEquals(expectedEnd, range.end());
        assertEquals(LocalTime.of(0, 0), range.openTime());
        assertEquals(LocalTime.of(23, 59), range.closeTime());
    }

    // ---------------------------------------------------------------
    // 4. getShiftTimeRangeForPeriod - multi-day range
    // ---------------------------------------------------------------
    @Test
    @DisplayName("getShiftTimeRangeForPeriod: multi-day uses first day start to last day end")
    void getShiftTimeRangeForPeriod_multiDay() {
        LocalDate startDate = LocalDate.of(2026, 3, 23); // Monday
        LocalDate endDate = LocalDate.of(2026, 3, 25);   // Wednesday

        BusinessHours mondayHours = buildHours(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(22, 0), false);
        BusinessHours wednesdayHours = buildHours(DayOfWeek.WEDNESDAY,
                LocalTime.of(10, 0), LocalTime.of(23, 0), false);

        when(businessHoursRepository.findByRestaurant_IdAndDayOfWeek(eq(RESTAURANT_ID), eq(DayOfWeek.MONDAY)))
                .thenReturn(Optional.of(mondayHours));
        when(businessHoursRepository.findByRestaurant_IdAndDayOfWeek(eq(RESTAURANT_ID), eq(DayOfWeek.WEDNESDAY)))
                .thenReturn(Optional.of(wednesdayHours));

        ShiftTimeService.ShiftTimeRange range =
                shiftTimeService.getShiftTimeRangeForPeriod(RESTAURANT_ID, startDate, endDate);

        assertNotNull(range);

        // Start from Monday 09:00
        OffsetDateTime expectedStart = startDate.atTime(9, 0).atZone(ZONE).toOffsetDateTime();
        // End at Wednesday 23:00
        OffsetDateTime expectedEnd = endDate.atTime(23, 0).atZone(ZONE).toOffsetDateTime();

        assertEquals(expectedStart, range.start());
        assertEquals(expectedEnd, range.end());
        // openTime from first shift, closeTime from last shift
        assertEquals(LocalTime.of(9, 0), range.openTime());
        assertEquals(LocalTime.of(23, 0), range.closeTime());
    }

    // ---------------------------------------------------------------
    // 5. getCurrentBusinessDay - before shift end returns today
    // ---------------------------------------------------------------
    @Test
    @DisplayName("getCurrentBusinessDay: during normal shift returns today")
    void getCurrentBusinessDay_beforeShiftEnd_returnsToday() {
        LocalDate today = LocalDate.of(2026, 3, 25); // Wednesday
        LocalDate yesterday = LocalDate.of(2026, 3, 24); // Tuesday
        LocalTime now = LocalTime.of(14, 0); // 2 PM, well within a normal shift

        // Yesterday = Tuesday, normal hours, no cross-midnight
        BusinessHours tuesdayHours = buildHours(DayOfWeek.TUESDAY,
                LocalTime.of(9, 0), LocalTime.of(22, 0), false);
        // Today = Wednesday, normal hours
        BusinessHours wednesdayHours = buildHours(DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0), LocalTime.of(22, 0), false);

        when(businessHoursRepository.findByRestaurant_IdAndDayOfWeek(eq(RESTAURANT_ID), eq(DayOfWeek.TUESDAY)))
                .thenReturn(Optional.of(tuesdayHours));
        when(businessHoursRepository.findByRestaurant_IdAndDayOfWeek(eq(RESTAURANT_ID), eq(DayOfWeek.WEDNESDAY)))
                .thenReturn(Optional.of(wednesdayHours));

        try (MockedStatic<LocalDate> mockedDate = mockStatic(LocalDate.class);
             MockedStatic<LocalTime> mockedTime = mockStatic(LocalTime.class)) {

            mockedDate.when(LocalDate::now).thenReturn(today);
            mockedTime.when(LocalTime::now).thenReturn(now);

            LocalDate result = shiftTimeService.getCurrentBusinessDay(RESTAURANT_ID);

            assertEquals(today, result);
        }
    }

    // ---------------------------------------------------------------
    // 6. getCurrentBusinessDay - after midnight in cross-midnight shift
    //    returns previous day
    // ---------------------------------------------------------------
    @Test
    @DisplayName("getCurrentBusinessDay: after midnight during cross-midnight shift returns yesterday")
    void getCurrentBusinessDay_afterMidnight_returnsPreviousDay() {
        LocalDate today = LocalDate.of(2026, 3, 26); // Thursday
        LocalDate yesterday = LocalDate.of(2026, 3, 25); // Wednesday
        LocalTime now = LocalTime.of(1, 0); // 1 AM - still in yesterday's shift

        // Yesterday (Wednesday) has cross-midnight shift: 21:00 - 03:00
        BusinessHours wednesdayHours = buildHours(DayOfWeek.WEDNESDAY,
                LocalTime.of(21, 0), LocalTime.of(3, 0), false);

        when(businessHoursRepository.findByRestaurant_IdAndDayOfWeek(eq(RESTAURANT_ID), eq(DayOfWeek.WEDNESDAY)))
                .thenReturn(Optional.of(wednesdayHours));

        try (MockedStatic<LocalDate> mockedDate = mockStatic(LocalDate.class);
             MockedStatic<LocalTime> mockedTime = mockStatic(LocalTime.class)) {

            mockedDate.when(LocalDate::now).thenReturn(today);
            mockedTime.when(LocalTime::now).thenReturn(now);

            LocalDate result = shiftTimeService.getCurrentBusinessDay(RESTAURANT_ID);

            assertEquals(yesterday, result);
        }
    }

    // ---------------------------------------------------------------
    // 7. isRevenueStatus - completed and delivered are true
    // ---------------------------------------------------------------
    @Test
    @DisplayName("isRevenueStatus: COMPLETED and DELIVERED return true")
    void isRevenueStatus_completedAndDelivered_true() {
        assertTrue(shiftTimeService.isRevenueStatus(OrderStatus.COMPLETED));
        assertTrue(shiftTimeService.isRevenueStatus(OrderStatus.DELIVERED));
        assertTrue(shiftTimeService.isRevenueStatus(OrderStatus.ACCEPTED));
        assertTrue(shiftTimeService.isRevenueStatus(OrderStatus.PREPARING));
        assertTrue(shiftTimeService.isRevenueStatus(OrderStatus.READY));
        assertTrue(shiftTimeService.isRevenueStatus(OrderStatus.PICKED_UP));
        assertTrue(shiftTimeService.isRevenueStatus(OrderStatus.COURIER_ASSIGNED));
        assertTrue(shiftTimeService.isRevenueStatus(OrderStatus.ON_DELIVERY));
    }

    // ---------------------------------------------------------------
    // 8. isRevenueStatus - cancelled is false
    // ---------------------------------------------------------------
    @Test
    @DisplayName("isRevenueStatus: CANCELLED and other non-revenue statuses return false")
    void isRevenueStatus_cancelled_false() {
        assertFalse(shiftTimeService.isRevenueStatus(OrderStatus.CANCELLED));
        assertFalse(shiftTimeService.isRevenueStatus(OrderStatus.REJECTED));
        assertFalse(shiftTimeService.isRevenueStatus(OrderStatus.PENDING));
        assertFalse(shiftTimeService.isRevenueStatus(OrderStatus.NEW));
        assertFalse(shiftTimeService.isRevenueStatus(OrderStatus.PLACED));
    }
}

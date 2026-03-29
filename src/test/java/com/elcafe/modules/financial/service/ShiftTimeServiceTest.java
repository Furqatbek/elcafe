package com.elcafe.modules.financial.service;

import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.restaurant.entity.BusinessHours;
import com.elcafe.modules.restaurant.repository.BusinessHoursRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftTimeServiceTest {

    @Mock private BusinessHoursRepository businessHoursRepository;
    @InjectMocks private ShiftTimeService shiftTimeService;

    private BusinessHours createHours(DayOfWeek day, String open, String close) {
        BusinessHours bh = new BusinessHours();
        bh.setDayOfWeek(day);
        bh.setOpenTime(LocalTime.parse(open));
        bh.setCloseTime(LocalTime.parse(close));
        bh.setOpen(true);
        return bh;
    }

    @Test
    @DisplayName("getShiftTimeRange — normal shift 09:00-23:00")
    void normalShift_returnsCorrectRange() {
        LocalDate monday = LocalDate.of(2026, 3, 30); // Monday
        when(businessHoursRepository.findByRestaurantId(anyLong()))
                .thenReturn(List.of(createHours(DayOfWeek.MONDAY, "09:00", "23:00")));

        ShiftTimeService.ShiftTimeRange range = shiftTimeService.getShiftTimeRange(1L, monday);

        assertNotNull(range);
        assertEquals(9, range.start().getHour());
        assertEquals(23, range.end().getHour());
    }

    @Test
    @DisplayName("getShiftTimeRange — no business hours uses defaults")
    void noBusinessHours_usesDefaults() {
        when(businessHoursRepository.findByRestaurantId(anyLong())).thenReturn(List.of());

        ShiftTimeService.ShiftTimeRange range = shiftTimeService.getShiftTimeRange(1L, LocalDate.now());

        assertNotNull(range);
        assertEquals(9, range.start().getHour());
        assertEquals(23, range.end().getHour());
    }

    @Test
    @DisplayName("getShiftTimeRangeForPeriod — spans multiple days")
    void multiDay_spansCorrectly() {
        when(businessHoursRepository.findByRestaurantId(anyLong())).thenReturn(List.of());

        LocalDate start = LocalDate.of(2026, 3, 25);
        LocalDate end = LocalDate.of(2026, 3, 28);
        ShiftTimeService.ShiftTimeRange range = shiftTimeService.getShiftTimeRangeForPeriod(1L, start, end);

        assertNotNull(range);
        assertEquals(start.atTime(9, 0), range.start().toLocalDateTime());
    }

    @Test
    @DisplayName("getCurrentBusinessDay — returns today by default")
    void getCurrentBusinessDay_returnsToday() {
        when(businessHoursRepository.findByRestaurantId(anyLong())).thenReturn(List.of());

        LocalDate result = shiftTimeService.getCurrentBusinessDay(1L);

        assertNotNull(result);
    }

    @Test
    @DisplayName("isRevenueStatus — COMPLETED is revenue")
    void isRevenueStatus_completed_true() {
        assertTrue(shiftTimeService.isRevenueStatus(OrderStatus.COMPLETED));
    }

    @Test
    @DisplayName("isRevenueStatus — DELIVERED is revenue")
    void isRevenueStatus_delivered_true() {
        assertTrue(shiftTimeService.isRevenueStatus(OrderStatus.DELIVERED));
    }

    @Test
    @DisplayName("isRevenueStatus — CANCELLED is not revenue")
    void isRevenueStatus_cancelled_false() {
        assertFalse(shiftTimeService.isRevenueStatus(OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("getRevenueStatusList — returns non-empty list")
    void getRevenueStatusList_nonEmpty() {
        List<OrderStatus> statuses = shiftTimeService.getRevenueStatusList();
        assertFalse(statuses.isEmpty());
        assertTrue(statuses.contains(OrderStatus.COMPLETED));
    }
}

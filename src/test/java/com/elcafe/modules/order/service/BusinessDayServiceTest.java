package com.elcafe.modules.order.service;

import com.elcafe.modules.restaurant.entity.WorkingHours;
import com.elcafe.modules.restaurant.repository.WorkingHoursRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessDayServiceTest {

    @Mock private WorkingHoursRepository workingHoursRepository;
    @InjectMocks private BusinessDayService businessDayService;

    @Test
    @DisplayName("getBusinessDayRange — returns date range")
    void getBusinessDayRange_returnsRange() {
        when(workingHoursRepository.findByRestaurant_Id(anyLong())).thenReturn(List.of());

        BusinessDayService.DateRange range = businessDayService.getBusinessDayRange(1L, LocalDate.now());

        assertNotNull(range);
        assertNotNull(range.from());
        assertNotNull(range.to());
    }

    @Test
    @DisplayName("getBusinessDayRangeForPeriod — returns range spanning multiple days")
    void getBusinessDayRangeForPeriod_returnsRange() {
        when(workingHoursRepository.findByRestaurant_Id(anyLong())).thenReturn(List.of());

        BusinessDayService.DateRange range = businessDayService.getBusinessDayRangeForPeriod(
                1L, LocalDate.now().minusDays(7), LocalDate.now());

        assertNotNull(range);
    }
}

package com.elcafe.modules.financial.service;

import com.elcafe.modules.restaurant.entity.BusinessHours;
import com.elcafe.modules.restaurant.repository.BusinessHoursRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The batch {@link ShiftTimeService.BusinessDayResolver} (hours preloaded once, PERF-2 fix) must
 * attribute every timestamp to exactly the same business day as the per-call, repository-backed
 * {@link ShiftTimeService#getBusinessDay} — otherwise daily revenue silently shifts between days.
 * This sweeps both paths across the interesting shapes: normal hours, midnight-crossing shifts
 * (before/at/after today's open), closed days, missing rows, and a null restaurant.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BusinessDayResolverTest {

    private static final Long RESTAURANT_ID = 42L;

    @Mock private BusinessHoursRepository businessHoursRepository;

    private BusinessHours hours(DayOfWeek day, LocalTime open, LocalTime close, boolean closed) {
        BusinessHours h = new BusinessHours();
        h.setDayOfWeek(day);
        h.setOpenTime(open);
        h.setCloseTime(close);
        h.setClosed(closed);
        return h;
    }

    /** Stub both lookup styles (per-day and all-at-once) from the same authoritative map. */
    private ShiftTimeService serviceWith(Map<DayOfWeek, BusinessHours> hoursByDay) {
        when(businessHoursRepository.findByRestaurant_IdAndDayOfWeek(eq(RESTAURANT_ID), any(DayOfWeek.class)))
                .thenAnswer(inv -> Optional.ofNullable(hoursByDay.get(inv.getArgument(1, DayOfWeek.class))));
        when(businessHoursRepository.findByRestaurant_Id(RESTAURANT_ID))
                .thenReturn(List.copyOf(hoursByDay.values()));
        return new ShiftTimeService(businessHoursRepository);
    }

    private void assertBothPathsAgree(ShiftTimeService service, LocalDateTime timestamp, LocalDate expected) {
        assertThat(service.getBusinessDay(RESTAURANT_ID, timestamp))
                .as("per-call path for %s", timestamp).isEqualTo(expected);
        assertThat(service.businessDayResolver(RESTAURANT_ID).businessDayFor(timestamp))
                .as("batch resolver path for %s", timestamp).isEqualTo(expected);
    }

    @Test
    @DisplayName("normal (non-crossing) hours: every timestamp keeps its calendar date")
    void normalHours() {
        // 2026-07-06 is a Monday
        Map<DayOfWeek, BusinessHours> week = Map.of(
                DayOfWeek.MONDAY, hours(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), false),
                DayOfWeek.TUESDAY, hours(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), false));
        ShiftTimeService service = serviceWith(week);

        assertBothPathsAgree(service, LocalDateTime.of(2026, 7, 7, 1, 30), LocalDate.of(2026, 7, 7));
        assertBothPathsAgree(service, LocalDateTime.of(2026, 7, 7, 12, 0), LocalDate.of(2026, 7, 7));
    }

    @Test
    @DisplayName("midnight-crossing shift: before today's open belongs to yesterday, after belongs to today")
    void midnightCrossingShift() {
        // Monday 11:00 → 02:00 Tuesday; Tuesday opens 11:00
        Map<DayOfWeek, BusinessHours> week = Map.of(
                DayOfWeek.MONDAY, hours(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(2, 0), false),
                DayOfWeek.TUESDAY, hours(DayOfWeek.TUESDAY, LocalTime.of(11, 0), LocalTime.of(2, 0), false));
        ShiftTimeService service = serviceWith(week);

        LocalDate monday = LocalDate.of(2026, 7, 6);
        LocalDate tuesday = LocalDate.of(2026, 7, 7);
        // 01:00 Tuesday — inside Monday's after-midnight tail
        assertBothPathsAgree(service, tuesday.atTime(1, 0), monday);
        // 10:59 Tuesday — still before Tuesday's open → attributed to Monday (existing semantics)
        assertBothPathsAgree(service, tuesday.atTime(10, 59), monday);
        // 11:00 Tuesday — at open → Tuesday
        assertBothPathsAgree(service, tuesday.atTime(11, 0), tuesday);
        // evening → Tuesday
        assertBothPathsAgree(service, tuesday.atTime(20, 0), tuesday);
    }

    @Test
    @DisplayName("open == close counts as crossing; closed or missing yesterday means calendar date")
    void edgeShapes() {
        LocalDate tuesday = LocalDate.of(2026, 7, 7);

        // open == close (24h-style) — crossing; today's row missing → fallback to yesterday's open
        ShiftTimeService twentyFourSeven = serviceWith(Map.of(
                DayOfWeek.MONDAY, hours(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(8, 0), false)));
        assertBothPathsAgree(twentyFourSeven, tuesday.atTime(7, 59), tuesday.minusDays(1));
        assertBothPathsAgree(twentyFourSeven, tuesday.atTime(8, 0), tuesday);

        // yesterday closed → calendar date even at 01:00
        ShiftTimeService closedMonday = serviceWith(Map.of(
                DayOfWeek.MONDAY, hours(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(2, 0), true)));
        assertBothPathsAgree(closedMonday, tuesday.atTime(1, 0), tuesday);

        // no hours configured at all → calendar date
        ShiftTimeService noHours = serviceWith(Map.of());
        assertBothPathsAgree(noHours, tuesday.atTime(1, 0), tuesday);

        // crossing yesterday + today marked closed → falls back to yesterday's open time
        ShiftTimeService todayClosed = serviceWith(Map.of(
                DayOfWeek.MONDAY, hours(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(2, 0), false),
                DayOfWeek.TUESDAY, hours(DayOfWeek.TUESDAY, LocalTime.of(11, 0), LocalTime.of(2, 0), true)));
        assertBothPathsAgree(todayClosed, tuesday.atTime(10, 0), tuesday.minusDays(1));
        assertBothPathsAgree(todayClosed, tuesday.atTime(11, 0), tuesday);
    }

    @Test
    @DisplayName("null restaurant: both paths return the calendar date without touching the repository")
    void nullRestaurant() {
        ShiftTimeService service = new ShiftTimeService(businessHoursRepository);
        LocalDateTime ts = LocalDateTime.of(2026, 7, 7, 1, 0);
        assertThat(service.getBusinessDay(null, ts)).isEqualTo(ts.toLocalDate());
        assertThat(service.businessDayResolver(null).businessDayFor(ts)).isEqualTo(ts.toLocalDate());
    }
}

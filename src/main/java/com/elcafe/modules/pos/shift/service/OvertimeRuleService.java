package com.elcafe.modules.pos.shift.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.entity.ShiftRules;
import com.elcafe.modules.pos.shift.repository.ShiftRulesRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class OvertimeRuleService {

    private final ShiftRulesRepository rulesRepository;
    private final RestaurantRepository restaurantRepository;

    /**
     * Get or create default rules for a restaurant.
     */
    @Transactional
    public ShiftRules getOrCreateRules(Long restaurantId) {
        return rulesRepository.findByRestaurantId(restaurantId).orElseGet(() -> {
            Restaurant restaurant = restaurantRepository.findById(restaurantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
            ShiftRules rules = ShiftRules.builder().restaurant(restaurant).build();
            return rulesRepository.save(rules);
        });
    }

    @Transactional
    public ShiftRules updateRules(Long restaurantId, ShiftRules updated) {
        ShiftRules rules = getOrCreateRules(restaurantId);
        if (updated.getMaxShiftHours() != null) rules.setMaxShiftHours(updated.getMaxShiftHours());
        if (updated.getMaxWeeklyHours() != null) rules.setMaxWeeklyHours(updated.getMaxWeeklyHours());
        if (updated.getOvertimeMultiplier() != null) rules.setOvertimeMultiplier(updated.getOvertimeMultiplier());
        if (updated.getMinBreakAfterHours() != null) rules.setMinBreakAfterHours(updated.getMinBreakAfterHours());
        if (updated.getMinBreakDurationMinutes() != null) rules.setMinBreakDurationMinutes(updated.getMinBreakDurationMinutes());
        if (updated.getNotifyOvertimeAtHours() != null) rules.setNotifyOvertimeAtHours(updated.getNotifyOvertimeAtHours());
        if (updated.getAutoClockOutAfterHours() != null) rules.setAutoClockOutAfterHours(updated.getAutoClockOutAfterHours());
        return rulesRepository.save(rules);
    }

    /**
     * Check if a shift has exceeded max hours (overtime).
     */
    public boolean isOvertime(EmployeeShift shift, ShiftRules rules) {
        long workedMinutes = shift.getWorkedMinutes();
        return workedMinutes > (long) rules.getMaxShiftHours() * 60;
    }

    /**
     * Check if a break is required (worked > minBreakAfterHours without break).
     */
    public boolean isBreakRequired(EmployeeShift shift, ShiftRules rules) {
        long workedMinutes = shift.getWorkedMinutes();
        int breakMinutes = shift.getBreakMinutes() != null ? shift.getBreakMinutes() : 0;
        return workedMinutes > (long) rules.getMinBreakAfterHours() * 60 && breakMinutes < rules.getMinBreakDurationMinutes();
    }

    /**
     * Check if auto-clock-out should be triggered.
     */
    public boolean shouldAutoClockOut(EmployeeShift shift, ShiftRules rules) {
        long workedMinutes = shift.getWorkedMinutes();
        return workedMinutes >= (long) rules.getAutoClockOutAfterHours() * 60;
    }

    /**
     * Calculate overtime pay for a shift.
     * Overtime hours = max(0, worked hours - max shift hours)
     * Overtime pay = overtime hours × hourlyRate × overtimeMultiplier
     */
    public BigDecimal calculateOvertimePay(EmployeeShift shift, ShiftRules rules, BigDecimal hourlyRate) {
        long workedMinutes = shift.getWorkedMinutes();
        long maxMinutes = (long) rules.getMaxShiftHours() * 60;
        long overtimeMinutes = Math.max(0, workedMinutes - maxMinutes);

        if (overtimeMinutes == 0 || hourlyRate == null) return BigDecimal.ZERO;

        BigDecimal overtimeHours = BigDecimal.valueOf(overtimeMinutes).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        return overtimeHours.multiply(hourlyRate).multiply(rules.getOvertimeMultiplier())
                .setScale(2, RoundingMode.HALF_UP);
    }
}

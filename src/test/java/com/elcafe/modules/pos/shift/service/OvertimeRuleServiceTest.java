package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.entity.ShiftRules;
import com.elcafe.modules.pos.shift.enums.ShiftStatus;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import com.elcafe.modules.pos.shift.repository.ShiftRulesRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OvertimeRuleServiceTest {

    @Mock private ShiftRulesRepository rulesRepository;
    @Mock private EmployeeShiftRepository shiftRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @InjectMocks private OvertimeRuleService service;

    private ShiftRules rules;
    private EmployeeShift shift;

    @BeforeEach
    void setUp() {
        rules = ShiftRules.builder()
                .maxShiftHours(8)
                .maxWeeklyHours(40)
                .overtimeMultiplier(new BigDecimal("1.50"))
                .minBreakAfterHours(4)
                .minBreakDurationMinutes(30)
                .notifyOvertimeAtHours(7)
                .autoClockOutAfterHours(12)
                .build();

        User employee = new User();
        employee.setId(10L);

        shift = new EmployeeShift();
        shift.setId(100L);
        shift.setEmployee(employee);
        shift.setStatus(ShiftStatus.ACTIVE);
    }

    private void setWorkedHours(int hours, int minutes) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        shift.setClockIn(now.minusHours(hours).minusMinutes(minutes));
        // clockOut is null for active shift, workedMinutes uses current time
    }

    @Test @DisplayName("overtime detected when hours > max")
    void overtimeDetected() {
        setWorkedHours(9, 0); // 9h > 8h max
        assertThat(service.isOvertime(shift, rules)).isTrue();
    }

    @Test @DisplayName("no overtime when within limit")
    void noOvertime() {
        setWorkedHours(7, 30); // 7.5h < 8h max
        assertThat(service.isOvertime(shift, rules)).isFalse();
    }

    @Test @DisplayName("break required after configured hours without break")
    void breakRequired() {
        setWorkedHours(5, 0); // 5h > 4h threshold
        shift.setBreakMinutes(0); // no break taken
        assertThat(service.isBreakRequired(shift, rules)).isTrue();
    }

    @Test @DisplayName("break not required when break was taken")
    void breakNotRequired() {
        setWorkedHours(5, 0);
        shift.setBreakMinutes(30); // 30min break taken
        assertThat(service.isBreakRequired(shift, rules)).isFalse();
    }

    @Test @DisplayName("auto-clock-out triggered at max hours")
    void autoClockOut() {
        setWorkedHours(12, 0); // 12h = autoClockOutAfterHours
        assertThat(service.shouldAutoClockOut(shift, rules)).isTrue();
    }

    @Test @DisplayName("auto-clock-out not triggered below max")
    void noAutoClockOut() {
        setWorkedHours(11, 30);
        assertThat(service.shouldAutoClockOut(shift, rules)).isFalse();
    }

    @Test @DisplayName("overtime pay multiplier applied correctly")
    void overtimePay() {
        setWorkedHours(10, 0); // 10h = 2h overtime
        BigDecimal hourlyRate = new BigDecimal("25000");

        BigDecimal pay = service.calculateOvertimePay(shift, rules, hourlyRate);

        // 2h overtime × 25000 × 1.5 = 75000
        assertThat(pay).isEqualByComparingTo("75000");
    }

    @Test @DisplayName("no overtime pay when within max hours")
    void noOvertimePay() {
        setWorkedHours(7, 0);
        BigDecimal hourlyRate = new BigDecimal("25000");

        BigDecimal pay = service.calculateOvertimePay(shift, rules, hourlyRate);

        assertThat(pay).isEqualByComparingTo("0");
    }
}

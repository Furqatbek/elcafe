package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.pos.shift.entity.ConsumptionAllowance;
import com.elcafe.modules.pos.shift.entity.ConsumptionAllowance.Period;
import com.elcafe.modules.pos.shift.entity.EmployeeConsumption;
import com.elcafe.modules.pos.shift.repository.ConsumptionAllowanceRepository;
import com.elcafe.modules.pos.shift.repository.EmployeeConsumptionRepository;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.waiter.entity.Waiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsumptionLimitServiceTest {

    private static final Long RESTAURANT_ID = 1L;
    private static final Long EMPLOYEE_ID = 10L;
    private static final Long COFFEE_CAT_ID = 100L;

    @Mock private ConsumptionAllowanceRepository allowanceRepository;
    @Mock private EmployeeConsumptionRepository consumptionRepository;
    @Mock private EmployeeShiftRepository shiftRepository;

    @InjectMocks
    private ConsumptionLimitService service;

    private Restaurant restaurant;
    private User employee;
    private Category coffee;
    private Product latte;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);

        employee = new User();
        employee.setId(EMPLOYEE_ID);

        coffee = new Category();
        coffee.setId(COFFEE_CAT_ID);
        coffee.setName("Coffee");

        latte = Product.builder()
                .id(500L)
                .name("Latte")
                .price(new BigDecimal("20000"))
                .category(coffee)
                .build();
    }

    private ConsumptionAllowance rule(Period period, Integer limitCount, BigDecimal limitAmount,
                                       Category category, User emp, Waiter w) {
        return ConsumptionAllowance.builder()
                .id(1L)
                .restaurant(restaurant)
                .category(category)
                .employee(emp)
                .waiter(w)
                .period(period)
                .limitCount(limitCount)
                .limitAmount(limitAmount)
                .active(true)
                .build();
    }

    private EmployeeConsumption priorConsumption(int qty, BigDecimal totalCost) {
        return EmployeeConsumption.builder()
                .id(System.nanoTime())
                .restaurant(restaurant)
                .employee(employee)
                .product(latte)
                .quantity(qty)
                .totalCost(totalCost)
                .consumedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("no matching rule → unlimited (everything free)")
    void noRule() {
        when(allowanceRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                .thenReturn(List.of());

        var d = service.evaluate(RESTAURANT_ID, employee, null, latte, 2);

        assertThat(d.overLimit()).isFalse();
        assertThat(d.allowance()).isNull();
        assertThat(d.freeAmount()).isEqualByComparingTo("40000");
        assertThat(d.chargedAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("under count cap → fully free")
    void underCountCap() {
        ConsumptionAllowance r = rule(Period.DAILY, 3, null, coffee, null, null);
        when(allowanceRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                .thenReturn(List.of(r));
        when(consumptionRepository
                .findByRestaurant_IdAndConsumedAtBetweenOrderByConsumedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of(priorConsumption(1, new BigDecimal("20000"))));

        var d = service.evaluate(RESTAURANT_ID, employee, null, latte, 2);

        assertThat(d.overLimit()).isFalse();
        assertThat(d.freeAmount()).isEqualByComparingTo("40000");
        assertThat(d.remainingCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("over count cap → only overflow is charged")
    void splitOverCountCap() {
        ConsumptionAllowance r = rule(Period.DAILY, 3, null, coffee, null, null);
        when(allowanceRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                .thenReturn(List.of(r));
        // Already consumed 2 lattes. Ordering 2 more — 1 free, 1 charged.
        when(consumptionRepository
                .findByRestaurant_IdAndConsumedAtBetweenOrderByConsumedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of(priorConsumption(2, new BigDecimal("40000"))));

        var d = service.evaluate(RESTAURANT_ID, employee, null, latte, 2);

        assertThat(d.overLimit()).isTrue();
        assertThat(d.freeAmount()).isEqualByComparingTo("20000");
        assertThat(d.chargedAmount()).isEqualByComparingTo("20000");
        assertThat(d.remainingCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("both caps applied: hitting the money cap first")
    void moneyCapWinsWhenMoreRestrictive() {
        // Count cap is generous (10), money cap is the tight one (30k/day).
        ConsumptionAllowance r = rule(Period.DAILY, 10, new BigDecimal("30000"), coffee, null, null);
        when(allowanceRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                .thenReturn(List.of(r));
        when(consumptionRepository
                .findByRestaurant_IdAndConsumedAtBetweenOrderByConsumedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of(priorConsumption(0, new BigDecimal("20000"))));
        // Ordering 1 × 20000 — total prior 20k + 20k = 40k, money cap 30k.
        // Free portion 10k, charged 10k.
        var d = service.evaluate(RESTAURANT_ID, employee, null, latte, 1);

        assertThat(d.overLimit()).isTrue();
        assertThat(d.freeAmount()).isEqualByComparingTo("10000");
        assertThat(d.chargedAmount()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("employee-specific rule wins over restaurant-wide for same category")
    void specificityWins() {
        ConsumptionAllowance shopWide = rule(Period.DAILY, 5, null, coffee, null, null);
        ConsumptionAllowance forEmp = rule(Period.DAILY, 1, null, coffee, employee, null);
        when(allowanceRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                .thenReturn(List.of(shopWide, forEmp));
        when(consumptionRepository
                .findByRestaurant_IdAndConsumedAtBetweenOrderByConsumedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of());

        var d = service.evaluate(RESTAURANT_ID, employee, null, latte, 2);

        // Tighter per-employee rule of 1 should kick in, so 1 free + 1 charged.
        assertThat(d.allowance().getId()).isEqualTo(forEmp.getId());
        assertThat(d.overLimit()).isTrue();
        assertThat(d.freeAmount()).isEqualByComparingTo("20000");
        assertThat(d.chargedAmount()).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("rule for a different category does not apply")
    void wrongCategoryIgnored() {
        Category bakery = new Category();
        bakery.setId(200L);
        ConsumptionAllowance rb = rule(Period.DAILY, 1, null, bakery, null, null);
        when(allowanceRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                .thenReturn(List.of(rb));
        when(consumptionRepository
                .findByRestaurant_IdAndConsumedAtBetweenOrderByConsumedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of());

        var d = service.evaluate(RESTAURANT_ID, employee, null, latte, 2);

        assertThat(d.allowance()).isNull();
        assertThat(d.overLimit()).isFalse();
    }

    @Test
    @DisplayName("role-scoped rule applies to every employee with that role name")
    void roleScopedRule() {
        employee.setRole(com.elcafe.modules.auth.enums.UserRole.WAITER);
        ConsumptionAllowance roleRule = ConsumptionAllowance.builder()
                .id(11L)
                .restaurant(restaurant)
                .role("WAITER")
                .period(Period.DAILY)
                .limitCount(2)
                .billOverflow(true)
                .active(true)
                .build();
        when(allowanceRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                .thenReturn(List.of(roleRule));
        when(consumptionRepository
                .findByRestaurant_IdAndConsumedAtBetweenOrderByConsumedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of());

        var d = service.evaluate(RESTAURANT_ID, employee, null, latte, 3);

        assertThat(d.allowance().getId()).isEqualTo(11L);
        assertThat(d.overLimit()).isTrue();
        assertThat(d.chargedAmount()).isEqualByComparingTo("20000");
        assertThat(d.shouldAutoCharge()).isTrue();
    }

    @Test
    @DisplayName("exact subject beats role beats restaurant-wide on specificity")
    void specificityOrdering() {
        employee.setRole(com.elcafe.modules.auth.enums.UserRole.WAITER);
        ConsumptionAllowance shopWide = rule(Period.DAILY, 10, null, null, null, null);
        ConsumptionAllowance forRole = ConsumptionAllowance.builder()
                .id(20L)
                .restaurant(restaurant)
                .role("WAITER")
                .period(Period.DAILY)
                .limitCount(5)
                .billOverflow(true)
                .active(true)
                .build();
        ConsumptionAllowance forEmp = rule(Period.DAILY, 1, null, null, employee, null);
        when(allowanceRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                .thenReturn(List.of(shopWide, forRole, forEmp));
        when(consumptionRepository
                .findByRestaurant_IdAndConsumedAtBetweenOrderByConsumedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of());

        var d = service.evaluate(RESTAURANT_ID, employee, null, latte, 1);

        // forEmp wins (exact subject), so the much tighter limit of 1 applies.
        assertThat(d.allowance().getId()).isEqualTo(forEmp.getId());
    }

    @Test
    @DisplayName("preview-only mode flags overflow but does not auto-charge")
    void previewOnlyMode() {
        ConsumptionAllowance manual = ConsumptionAllowance.builder()
                .id(30L)
                .restaurant(restaurant)
                .employee(employee)
                .period(Period.DAILY)
                .limitCount(1)
                .billOverflow(false)
                .active(true)
                .build();
        when(allowanceRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                .thenReturn(List.of(manual));
        when(consumptionRepository
                .findByRestaurant_IdAndConsumedAtBetweenOrderByConsumedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of());

        var d = service.evaluate(RESTAURANT_ID, employee, null, latte, 2);

        assertThat(d.overLimit()).isTrue();
        assertThat(d.shouldAutoCharge()).isFalse();
        assertThat(d.chargedAmount()).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("any-category rule applies when no category-specific rule exists")
    void anyCategoryRuleApplies() {
        ConsumptionAllowance any = rule(Period.DAILY, 2, null, null, null, null);
        when(allowanceRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                .thenReturn(List.of(any));
        when(consumptionRepository
                .findByRestaurant_IdAndConsumedAtBetweenOrderByConsumedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of(priorConsumption(2, new BigDecimal("40000"))));

        var d = service.evaluate(RESTAURANT_ID, employee, null, latte, 1);

        assertThat(d.overLimit()).isTrue();
        assertThat(d.chargedAmount()).isEqualByComparingTo("20000");
        assertThat(d.freeAmount()).isEqualByComparingTo("0");
    }
}

package uz.megahotdog.modules.pos.integration;

import uz.megahotdog.config.JpaConfig;
import uz.megahotdog.modules.auth.entity.User;
import uz.megahotdog.modules.auth.enums.UserRole;
import uz.megahotdog.modules.pos.shift.entity.EmployeeShift;
import uz.megahotdog.modules.pos.shift.entity.ShiftBreak;
import uz.megahotdog.modules.pos.shift.enums.BreakType;
import uz.megahotdog.modules.pos.shift.enums.ShiftStatus;
import uz.megahotdog.modules.pos.shift.repository.EmployeeShiftRepository;
import uz.megahotdog.modules.pos.shift.repository.ShiftBreakRepository;
import uz.megahotdog.modules.restaurant.entity.Restaurant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class ShiftIntegrationTest {

    @Autowired private EmployeeShiftRepository shiftRepository;
    @Autowired private ShiftBreakRepository breakRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private User employee;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);

        employee = User.builder()
                .email("cashier@test.com").password("pass")
                .firstName("Test").lastName("Employee")
                .role(UserRole.OPERATOR).build();
        em.persist(employee);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Full shift lifecycle: clockIn → break → clockOut")
    void fullShiftLifecycle() {
        User emp = em.find(User.class, employee.getId());

        // Clock in
        EmployeeShift shift = EmployeeShift.builder()
                .restaurant(restaurant).employee(emp)
                .shiftDate(LocalDate.now())
                .clockIn(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.ACTIVE)
                .openingCash(new BigDecimal("500000"))
                .build();
        shift = shiftRepository.save(shift);

        // Start break
        ShiftBreak shiftBreak = ShiftBreak.builder()
                .shift(shift)
                .breakStart(OffsetDateTime.now(ZoneOffset.UTC))
                .breakType(BreakType.MEAL).build();
        shiftBreak = breakRepository.save(shiftBreak);

        shift.setStatus(ShiftStatus.ON_BREAK);
        shiftRepository.save(shift);

        // End break
        shiftBreak.setBreakEnd(OffsetDateTime.now(ZoneOffset.UTC));
        breakRepository.save(shiftBreak);

        shift.setStatus(ShiftStatus.ACTIVE);
        shiftRepository.save(shift);

        // Clock out
        shift.setClockOut(OffsetDateTime.now(ZoneOffset.UTC));
        shift.setStatus(ShiftStatus.COMPLETED);
        shiftRepository.save(shift);

        em.flush();
        em.clear();

        // Verify
        EmployeeShift loaded = shiftRepository.findById(shift.getId()).orElseThrow();
        assertEquals(ShiftStatus.COMPLETED, loaded.getStatus());
        assertNotNull(loaded.getClockOut());

        List<ShiftBreak> breaks = breakRepository.findByShiftIdOrderByBreakStartAsc(shift.getId());
        assertEquals(1, breaks.size());
        assertNotNull(breaks.get(0).getBreakEnd());
    }

    @Test
    @DisplayName("Active shift query returns only ACTIVE status")
    void activeShiftQuery() {
        User emp = em.find(User.class, employee.getId());

        EmployeeShift active = shiftRepository.save(EmployeeShift.builder()
                .restaurant(restaurant).employee(emp)
                .shiftDate(LocalDate.now())
                .clockIn(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.ACTIVE).build());

        em.flush();
        em.clear();

        assertTrue(shiftRepository.findActiveShiftByEmployee(employee.getId()).isPresent());

        List<EmployeeShift> activeShifts = shiftRepository.findActiveShiftsByRestaurant(restaurant.getId());
        assertEquals(1, activeShifts.size());
    }

    @Test
    @DisplayName("Shifts by date returns correct date's shifts")
    void shiftsByDate() {
        User emp = em.find(User.class, employee.getId());

        shiftRepository.save(EmployeeShift.builder()
                .restaurant(restaurant).employee(emp)
                .shiftDate(LocalDate.now())
                .clockIn(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.COMPLETED).build());

        em.flush();
        em.clear();

        List<EmployeeShift> shifts = shiftRepository.findByRestaurantIdAndShiftDate(
                restaurant.getId(), LocalDate.now());
        assertEquals(1, shifts.size());

        List<EmployeeShift> yesterday = shiftRepository.findByRestaurantIdAndShiftDate(
                restaurant.getId(), LocalDate.now().minusDays(1));
        assertEquals(0, yesterday.size());
    }

    @Test
    @DisplayName("Pending approval query returns completed unapproved shifts")
    void pendingApproval() {
        User emp = em.find(User.class, employee.getId());

        shiftRepository.save(EmployeeShift.builder()
                .restaurant(restaurant).employee(emp)
                .shiftDate(LocalDate.now())
                .clockIn(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.COMPLETED).build());

        em.flush();
        em.clear();

        List<EmployeeShift> pending = shiftRepository.findPendingApproval(restaurant.getId());
        assertEquals(1, pending.size());
    }
}

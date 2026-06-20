package com.elcafe.modules.waiter.integration;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.entity.RestaurantTable.TableStatus;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.entity.WaiterTable;
import com.elcafe.modules.waiter.enums.WaiterRole;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import com.elcafe.modules.waiter.repository.WaiterTableRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class WaiterCrudIntegrationTest {

    @Autowired private WaiterRepository waiterRepository;
    @Autowired private WaiterTableRepository waiterTableRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private RestaurantTable table1;
    private RestaurantTable table2;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setCity("Tashkent");
        restaurant.setPhone("+998901234567");
        restaurant.setEmail("test@restaurant.com");
        restaurant.setActive(true);
        restaurant.setAcceptingOrders(true);
        restaurant.setDeliveryFee(BigDecimal.ZERO);
        em.persist(restaurant);

        table1 = createTable("T1");
        table2 = createTable("T2");
        em.flush();
    }

    private RestaurantTable createTable(String number) {
        RestaurantTable t = new RestaurantTable();
        t.setRestaurant(restaurant);
        t.setTableNumber(number);
        t.setTableName("Table " + number);
        t.setStatus(TableStatus.AVAILABLE);
        t.setCapacity(4);
        t.setActive(true);
        em.persist(t);
        return t;
    }

    private Waiter persistWaiter(String name, String pin, boolean active) {
        Waiter w = new Waiter();
        w.setRestaurantId(1L);
        w.setName(name);
        w.setPinCode(pin);
        w.setRole(WaiterRole.WAITER);
        w.setActive(active);
        return waiterRepository.save(w);
    }

    // ==================== Create ====================

    @Test
    @DisplayName("Create waiter persists all fields to database")
    void createWaiter_persistsAllFields() {
        Waiter w = new Waiter();
        w.setRestaurantId(1L);
        w.setName("Ali");
        w.setPinCode("1111");
        w.setEmail("ali@test.com");
        w.setPhoneNumber("+998901111111");
        w.setRole(WaiterRole.WAITER);
        w.setActive(true);
        w.setCommissionEnabled(true);
        w.setCommissionPercent(BigDecimal.valueOf(5));

        Waiter saved = waiterRepository.save(w);
        em.flush();
        em.clear();

        Waiter reloaded = waiterRepository.findById(saved.getId()).orElseThrow();
        assertEquals("Ali", reloaded.getName());
        assertEquals("1111", reloaded.getPinCode());
        assertEquals("ali@test.com", reloaded.getEmail());
        assertEquals("+998901111111", reloaded.getPhoneNumber());
        assertEquals(WaiterRole.WAITER, reloaded.getRole());
        assertTrue(reloaded.getActive());
        assertTrue(reloaded.getCommissionEnabled());
        assertEquals(0, BigDecimal.valueOf(5).compareTo(reloaded.getCommissionPercent()));
    }

    @Test
    @DisplayName("Multiple waiters with unique PINs all persist")
    void multipleWaiters_allPersisted() {
        persistWaiter("A", "1111", true);
        persistWaiter("B", "2222", true);
        persistWaiter("C", "3333", true);
        em.flush();
        em.clear();

        assertEquals(3, waiterRepository.count());
    }

    // ==================== Find by PIN ====================

    @Test
    @DisplayName("findByPinCode returns correct waiter")
    void findByPinCode_existing_returnsWaiter() {
        persistWaiter("Ali", "1234", true);
        em.flush();
        em.clear();

        Optional<Waiter> found = waiterRepository.findByPinCode("1234");
        assertTrue(found.isPresent());
        assertEquals("Ali", found.get().getName());
    }

    @Test
    @DisplayName("findByPinCode returns empty for non-existent PIN")
    void findByPinCode_nonExistent_returnsEmpty() {
        assertTrue(waiterRepository.findByPinCode("9999").isEmpty());
    }

    // ==================== Exists checks ====================

    @Test
    @DisplayName("existsByPinCode works correctly")
    void existsByPinCode_worksCorrectly() {
        persistWaiter("Ali", "1234", true);
        em.flush();

        assertTrue(waiterRepository.existsByPinCode("1234"));
        assertFalse(waiterRepository.existsByPinCode("9999"));
    }

    @Test
    @DisplayName("existsByEmail works correctly")
    void existsByEmail_worksCorrectly() {
        Waiter w = persistWaiter("Ali", "1234", true);
        w.setEmail("ali@test.com");
        waiterRepository.save(w);
        em.flush();

        assertTrue(waiterRepository.existsByEmail("ali@test.com"));
        assertFalse(waiterRepository.existsByEmail("nobody@test.com"));
    }

    // ==================== Active waiters ====================

    @Test
    @DisplayName("findByActiveTrueOrderByNameAsc returns only active, sorted by name")
    void findActiveWaiters_returnsSortedActiveOnly() {
        persistWaiter("Charlie", "3333", true);
        persistWaiter("Alice", "1111", true);
        persistWaiter("Inactive Bob", "2222", false);
        em.flush();
        em.clear();

        List<Waiter> active = waiterRepository.findByActiveTrueOrderByNameAsc();
        assertEquals(2, active.size());
        assertEquals("Alice", active.get(0).getName());
        assertEquals("Charlie", active.get(1).getName());
    }

    // ==================== Update ====================

    @Test
    @DisplayName("Updating waiter persists changes")
    void updateWaiter_changesPersisted() {
        Waiter w = persistWaiter("Old Name", "1234", true);
        Long id = w.getId();
        em.flush();
        em.clear();

        Waiter toUpdate = waiterRepository.findById(id).orElseThrow();
        toUpdate.setName("New Name");
        toUpdate.setPinCode("9999");
        toUpdate.setRole(WaiterRole.SUPERVISOR);
        waiterRepository.save(toUpdate);
        em.flush();
        em.clear();

        Waiter reloaded = waiterRepository.findById(id).orElseThrow();
        assertEquals("New Name", reloaded.getName());
        assertEquals("9999", reloaded.getPinCode());
        assertEquals(WaiterRole.SUPERVISOR, reloaded.getRole());
    }

    // ==================== Delete ====================

    @Test
    @DisplayName("Deleting waiter removes from database")
    void deleteWaiter_removesFromDatabase() {
        Waiter w = persistWaiter("ToDelete", "1234", true);
        Long id = w.getId();
        em.flush();

        waiterRepository.deleteById(id);
        em.flush();
        em.clear();

        assertFalse(waiterRepository.findById(id).isPresent());
    }

    // ==================== Table Assignment ====================

    @Test
    @DisplayName("Assigning waiter to table persists assignment")
    void assignWaiterToTable_persistsAssignment() {
        Waiter w = persistWaiter("Ali", "1234", true);
        em.flush();

        WaiterTable assignment = WaiterTable.builder()
                .waiter(w).table(table1).active(true).build();
        waiterTableRepository.save(assignment);
        em.flush();
        em.clear();

        List<WaiterTable> assignments = waiterTableRepository.findByWaiterIdAndActiveTrue(w.getId());
        assertEquals(1, assignments.size());
        assertEquals(table1.getId(), assignments.get(0).getTable().getId());
        assertTrue(assignments.get(0).getActive());
    }

    @Test
    @DisplayName("Unassigning sets active=false")
    void unassignWaiter_setsInactive() {
        Waiter w = persistWaiter("Ali", "1234", true);
        WaiterTable assignment = WaiterTable.builder()
                .waiter(w).table(table1).active(true).build();
        waiterTableRepository.save(assignment);
        em.flush();

        assignment.unassign();
        waiterTableRepository.save(assignment);
        em.flush();
        em.clear();

        assertEquals(0, waiterTableRepository.findByWaiterIdAndActiveTrue(w.getId()).size());
    }

    @Test
    @DisplayName("Only active assignments returned")
    void findActiveAssignments_returnsOnlyActive() {
        Waiter w = persistWaiter("Ali", "1234", true);
        waiterTableRepository.save(WaiterTable.builder().waiter(w).table(table1).active(true).build());
        waiterTableRepository.save(WaiterTable.builder().waiter(w).table(table2).active(false).build());
        em.flush();
        em.clear();

        List<WaiterTable> result = waiterTableRepository.findByWaiterIdAndActiveTrue(w.getId());
        assertEquals(1, result.size());
        assertEquals(table1.getId(), result.get(0).getTable().getId());
    }

    @Test
    @DisplayName("countByWaiterIdAndActiveTrue returns correct count")
    void countActiveAssignments_correct() {
        Waiter w = persistWaiter("Ali", "1234", true);
        waiterTableRepository.save(WaiterTable.builder().waiter(w).table(table1).active(true).build());
        waiterTableRepository.save(WaiterTable.builder().waiter(w).table(table2).active(true).build());
        em.flush();

        assertEquals(2, waiterTableRepository.countByWaiterIdAndActiveTrue(w.getId()));
    }

    @Test
    @DisplayName("findByTableIdAndActiveTrue finds assignment")
    void findByTableIdAndActiveTrue_correct() {
        Waiter w = persistWaiter("Ali", "1234", true);
        waiterTableRepository.save(WaiterTable.builder().waiter(w).table(table1).active(true).build());
        em.flush();
        em.clear();

        assertTrue(waiterTableRepository.findByTableIdAndActiveTrue(table1.getId()).isPresent());
        assertFalse(waiterTableRepository.findByTableIdAndActiveTrue(table2.getId()).isPresent());
    }
}

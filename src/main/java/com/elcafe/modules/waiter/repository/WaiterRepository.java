package com.elcafe.modules.waiter.repository;

import com.elcafe.common.tenant.AssignmentConfidence;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.enums.WaiterRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaiterRepository extends JpaRepository<Waiter, Long> {

    /** §3.7 review surface: waiters whose heuristic tenant assignment needs admin review. */
    List<Waiter> findByTenantAssignmentConfidence(AssignmentConfidence confidence);

    /**
     * Find waiter by PIN code for authentication
     */
    Optional<Waiter> findByPinCode(String pinCode);

    // --- Per-restaurant finders (V151: pin_code/email are unique per restaurant). ---

    /** Authenticate a waiter within a restaurant (PIN is only per-restaurant unique). */
    Optional<Waiter> findByRestaurantIdAndPinCode(Long restaurantId, String pinCode);

    boolean existsByRestaurantIdAndPinCode(Long restaurantId, String pinCode);

    boolean existsByRestaurantIdAndEmail(Long restaurantId, String email);

    /** Tenant-scoped fetch: the waiter must belong to this restaurant (e.g. when opening a shift). */
    Optional<Waiter> findByIdAndRestaurantId(Long id, Long restaurantId);

    /**
     * Find waiter by email and active status
     */
    Optional<Waiter> findByEmailAndActive(String email, Boolean active);

    /**
     * Find all waiters by role, sorted alphabetically by name
     */
    List<Waiter> findByRoleOrderByNameAsc(WaiterRole role);

    /**
     * Find all active waiters, sorted alphabetically by name
     */
    List<Waiter> findByActiveTrueOrderByNameAsc();

    List<Waiter> findAllByOrderByNameAsc();

    /**
     * Find all waiters for a restaurant, sorted alphabetically by name (tenant-scoped listing).
     */
    List<Waiter> findByRestaurantIdOrderByNameAsc(Long restaurantId);

    /**
     * Check if PIN code exists
     */
    boolean existsByPinCode(String pinCode);

    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);

    /**
     * Find waiters by role and active status, sorted alphabetically by name
     */
    List<Waiter> findByRoleAndActiveOrderByNameAsc(WaiterRole role, Boolean active);

    /**
     * Find waiters with active table assignments, sorted alphabetically by name
     */
    @Query("SELECT DISTINCT w FROM Waiter w " +
           "JOIN w.waiterTables wt " +
           "WHERE wt.active = true AND w.active = true " +
           "ORDER BY w.name ASC")
    List<Waiter> findWaitersWithActiveTables();
}

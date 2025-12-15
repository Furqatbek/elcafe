package com.elcafe.modules.waiter.repository;

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

    /**
     * Find waiter by PIN code for authentication
     */
    Optional<Waiter> findByPinCode(String pinCode);

    /**
     * Find waiter by email and active status
     */
    Optional<Waiter> findByEmailAndActive(String email, Boolean active);

    /**
     * Find all waiters by role
     */
    List<Waiter> findByRole(WaiterRole role);

    /**
     * Find all active waiters
     */
    List<Waiter> findByActiveTrue();

    /**
     * Check if PIN code exists
     */
    boolean existsByPinCode(String pinCode);

    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);

    /**
     * Find waiters by role and active status
     */
    List<Waiter> findByRoleAndActive(WaiterRole role, Boolean active);

    /**
     * Find waiters with active table assignments
     */
    @Query("SELECT DISTINCT w FROM Waiter w " +
           "JOIN w.waiterTables wt " +
           "WHERE wt.active = true AND w.active = true")
    List<Waiter> findWaitersWithActiveTables();
}

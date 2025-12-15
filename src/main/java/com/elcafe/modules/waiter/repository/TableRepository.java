package com.elcafe.modules.waiter.repository;

import com.elcafe.modules.waiter.entity.Table;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.enums.TableStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableRepository extends JpaRepository<Table, Long> {

    /**
     * Find tables by status
     */
    List<Table> findByStatus(TableStatus status);

    /**
     * Find tables by current waiter
     */
    List<Table> findByCurrentWaiter(Waiter waiter);

    /**
     * Find tables by current waiter ID
     */
    List<Table> findByCurrentWaiterId(Long waiterId);

    /**
     * Find tables by floor and section
     */
    List<Table> findByFloorAndSection(String floor, String section);

    /**
     * Find tables by floor
     */
    List<Table> findByFloor(String floor);

    /**
     * Find tables by section
     */
    List<Table> findBySection(String section);

    /**
     * Find available tables (FREE status and not merged)
     */
    @Query("SELECT t FROM Table t WHERE t.status = 'FREE' AND t.mergedWith IS NULL")
    List<Table> findAvailableTables();

    /**
     * Find table by number and restaurant ID
     */
    Optional<Table> findByRestaurantIdAndNumber(Long restaurantId, Integer number);

    /**
     * Find tables by restaurant ID
     */
    List<Table> findByRestaurantId(Long restaurantId);

    /**
     * Find tables by restaurant ID and status
     */
    List<Table> findByRestaurantIdAndStatus(Long restaurantId, TableStatus status);

    /**
     * Find merged tables (tables that are merged with another)
     */
    @Query("SELECT t FROM Table t WHERE t.mergedWith IS NOT NULL")
    List<Table> findMergedTables();

    /**
     * Find tables assigned to a waiter (through WaiterTable)
     */
    @Query("SELECT DISTINCT t FROM Table t " +
           "JOIN t.waiterTables wt " +
           "WHERE wt.waiter.id = :waiterId AND wt.active = true")
    List<Table> findByWaiterId(@Param("waiterId") Long waiterId);

    /**
     * Check if table number exists for a restaurant
     */
    boolean existsByRestaurantIdAndNumber(Long restaurantId, Integer number);
}

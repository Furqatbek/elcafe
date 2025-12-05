package com.elcafe.modules.waiter.repository;

import com.elcafe.modules.waiter.entity.WaiterTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WaiterTableRepository extends JpaRepository<WaiterTable, Long> {

    /**
     * Find active assignments for a waiter
     */
    List<WaiterTable> findByWaiterIdAndActiveTrue(Long waiterId);

    /**
     * Find active assignment for a table
     */
    Optional<WaiterTable> findByTableIdAndActiveTrue(Long tableId);

    /**
     * Find all active assignments
     */
    List<WaiterTable> findByActiveTrue();

    /**
     * Find assignments by waiter ID
     */
    List<WaiterTable> findByWaiterId(Long waiterId);

    /**
     * Find assignments by table ID
     */
    List<WaiterTable> findByTableId(Long tableId);

    /**
     * Find assignments within date range
     */
    @Query("SELECT wt FROM WaiterTable wt " +
           "WHERE wt.assignedAt BETWEEN :startDate AND :endDate")
    List<WaiterTable> findByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find waiter assignments within date range
     */
    @Query("SELECT wt FROM WaiterTable wt " +
           "WHERE wt.waiter.id = :waiterId " +
           "AND wt.assignedAt BETWEEN :startDate AND :endDate")
    List<WaiterTable> findByWaiterIdAndDateRange(
        @Param("waiterId") Long waiterId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count active assignments for a waiter
     */
    long countByWaiterIdAndActiveTrue(Long waiterId);

    /**
     * Find active assignment for specific waiter and table
     */
    Optional<WaiterTable> findByWaiterIdAndTableIdAndActiveTrue(Long waiterId, Long tableId);
}

package com.elcafe.modules.order.repository;

import com.elcafe.modules.order.entity.OrderTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderTableRepository extends JpaRepository<OrderTable, Long> {

    /**
     * Find all OrderTable entries for a given order.
     */
    List<OrderTable> findByOrderId(Long orderId);

    /**
     * Find all OrderTable entries for a given table.
     */
    List<OrderTable> findByTableId(Long tableId);

    /**
     * Find orders associated with a table that have specific statuses.
     * Useful for checking if a table is currently occupied.
     */
    @Query("SELECT ot FROM OrderTable ot WHERE ot.table.id = :tableId AND ot.order.status IN :statuses")
    List<OrderTable> findByTableIdAndOrderStatusIn(
            @Param("tableId") Long tableId,
            @Param("statuses") List<com.elcafe.modules.order.enums.OrderStatus> statuses);

    /**
     * Delete all OrderTable entries for a given order.
     */
    void deleteByOrderId(Long orderId);

    /**
     * Check if a table is associated with any active orders.
     */
    @Query("SELECT COUNT(ot) > 0 FROM OrderTable ot WHERE ot.table.id = :tableId " +
           "AND ot.order.status NOT IN ('DELIVERED', 'COMPLETED', 'CANCELLED', 'REJECTED')")
    boolean isTableOccupied(@Param("tableId") Long tableId);
}

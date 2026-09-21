package com.elcafe.modules.pos.cashdrawer.repository;

import com.elcafe.modules.pos.cashdrawer.entity.CashDrawerOperation;
import com.elcafe.modules.pos.cashdrawer.enums.CashOperationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface CashDrawerOperationRepository extends JpaRepository<CashDrawerOperation, Long> {

    List<CashDrawerOperation> findByCashDrawerIdOrderByCreatedAtDesc(Long cashDrawerId);

    Page<CashDrawerOperation> findByCashDrawerIdOrderByCreatedAtDesc(Long cashDrawerId, Pageable pageable);

    List<CashDrawerOperation> findByShiftIdOrderByCreatedAtAsc(Long shiftId);

    @Query("SELECT SUM(CASE WHEN o.operationType IN ('CASH_IN', 'PAID_IN') THEN o.amount " +
           "WHEN o.operationType IN ('CASH_OUT', 'PAID_OUT', 'DROP') THEN -o.amount ELSE 0 END) " +
           "FROM CashDrawerOperation o WHERE o.shift.id = :shiftId")
    BigDecimal calculateNetCashMovement(@Param("shiftId") Long shiftId);

    @Query("SELECT o FROM CashDrawerOperation o WHERE o.cashDrawer.id = :drawerId " +
           "AND o.createdAt BETWEEN :start AND :end ORDER BY o.createdAt")
    List<CashDrawerOperation> findByDrawerAndDateRange(@Param("drawerId") Long drawerId,
                                                       @Param("start") OffsetDateTime start,
                                                       @Param("end") OffsetDateTime end);

    List<CashDrawerOperation> findByCashDrawerIdAndOperationType(Long cashDrawerId, CashOperationType type);
}

package com.elcafe.modules.pos.shift.repository;

import com.elcafe.modules.pos.shift.entity.ShiftBreak;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftBreakRepository extends JpaRepository<ShiftBreak, Long> {

    List<ShiftBreak> findByShiftIdOrderByBreakStartAsc(Long shiftId);

    @Query("SELECT b FROM ShiftBreak b WHERE b.shift.id = :shiftId AND b.breakEnd IS NULL")
    Optional<ShiftBreak> findActiveBreak(@Param("shiftId") Long shiftId);

    @Query("SELECT SUM(TIMESTAMPDIFF(MINUTE, b.breakStart, COALESCE(b.breakEnd, CURRENT_TIMESTAMP))) " +
           "FROM ShiftBreak b WHERE b.shift.id = :shiftId")
    Integer calculateTotalBreakMinutes(@Param("shiftId") Long shiftId);
}

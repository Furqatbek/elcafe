package com.elcafe.modules.pos.shift.repository;

import com.elcafe.modules.pos.shift.entity.ShiftInventorySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShiftInventorySnapshotRepository extends JpaRepository<ShiftInventorySnapshot, Long> {

    List<ShiftInventorySnapshot> findByShiftIdAndSnapshotType(Long shiftId, ShiftInventorySnapshot.SnapshotType type);

    List<ShiftInventorySnapshot> findByShiftId(Long shiftId);

    List<ShiftInventorySnapshot> findByShiftIdAndVarianceIsNotNull(Long shiftId);
}

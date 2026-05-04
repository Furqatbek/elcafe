package com.elcafe.modules.pos.shift.repository;

import com.elcafe.modules.pos.shift.entity.ShiftSwapRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShiftSwapRequestRepository extends JpaRepository<ShiftSwapRequest, Long> {

    List<ShiftSwapRequest> findByRestaurantIdAndStatusOrderByCreatedAtDesc(Long restaurantId, ShiftSwapRequest.Status status);

    List<ShiftSwapRequest> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

    List<ShiftSwapRequest> findByTargetEmployeeIdAndStatus(Long employeeId, ShiftSwapRequest.Status status);
}

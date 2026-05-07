package com.elcafe.modules.pos.shift.repository;

import com.elcafe.modules.pos.shift.entity.EmployeeConsumption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface EmployeeConsumptionRepository extends JpaRepository<EmployeeConsumption, Long> {

    List<EmployeeConsumption> findByRestaurant_IdAndConsumedAtBetweenOrderByConsumedAtDesc(
            Long restaurantId, OffsetDateTime from, OffsetDateTime to);

    List<EmployeeConsumption> findByEmployeeShift_IdOrderByConsumedAtDesc(Long shiftId);

    List<EmployeeConsumption> findByWaiter_IdAndConsumedAtBetweenOrderByConsumedAtDesc(
            Long waiterId, OffsetDateTime from, OffsetDateTime to);
}

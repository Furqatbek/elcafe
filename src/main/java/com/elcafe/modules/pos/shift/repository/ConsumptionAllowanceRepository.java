package com.elcafe.modules.pos.shift.repository;

import com.elcafe.modules.pos.shift.entity.ConsumptionAllowance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsumptionAllowanceRepository extends JpaRepository<ConsumptionAllowance, Long> {

    List<ConsumptionAllowance> findByRestaurant_IdAndActiveTrue(Long restaurantId);

    List<ConsumptionAllowance> findByRestaurant_Id(Long restaurantId);
}

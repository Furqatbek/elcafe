package com.elcafe.modules.pos.shift.repository;

import com.elcafe.modules.pos.shift.entity.ShiftRules;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShiftRulesRepository extends JpaRepository<ShiftRules, Long> {

    Optional<ShiftRules> findByRestaurantId(Long restaurantId);
}

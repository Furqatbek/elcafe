package com.elcafe.modules.menu.repository;

import com.elcafe.modules.menu.entity.AddOnGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AddOnGroupRepository extends JpaRepository<AddOnGroup, Long> {

    List<AddOnGroup> findByRestaurantIdAndActiveTrue(Long restaurantId);
}

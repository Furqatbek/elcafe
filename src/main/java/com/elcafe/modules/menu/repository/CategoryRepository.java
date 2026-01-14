package com.elcafe.modules.menu.repository;

import com.elcafe.modules.menu.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByRestaurant_IdAndActiveTrueOrderBySortOrder(Long restaurantId);

    List<Category> findByRestaurant_IdOrderBySortOrder(Long restaurantId);
}

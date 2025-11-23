package com.elcafe.modules.restaurant.repository;

import com.elcafe.modules.restaurant.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long>, JpaSpecificationExecutor<Restaurant> {

    List<Restaurant> findByActiveTrue();

    List<Restaurant> findByActiveTrueAndAcceptingOrdersTrue();
}

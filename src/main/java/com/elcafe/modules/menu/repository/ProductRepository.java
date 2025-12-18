package com.elcafe.modules.menu.repository;

import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategoryIdOrderBySortOrder(Long categoryId);

    List<Product> findByCategoryIdAndStatusOrderBySortOrder(Long categoryId, ProductStatus status);

    @Query("SELECT p FROM Product p JOIN p.category c WHERE c.restaurant.id = :restaurantId AND p.status = :status ORDER BY c.sortOrder, p.sortOrder")
    List<Product> findByRestaurantIdAndStatus(@Param("restaurantId") Long restaurantId, @Param("status") ProductStatus status);

    @Query("SELECT p FROM Product p JOIN p.category c WHERE c.restaurant.id = :restaurantId ORDER BY c.sortOrder, p.sortOrder")
    List<Product> findByRestaurantId(@Param("restaurantId") Long restaurantId);
}

package com.elcafe.modules.menu.repository;

import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategoryIdOrderBySortOrder(Long categoryId);

    List<Product> findByCategoryIdAndStatusOrderBySortOrder(Long categoryId, ProductStatus status);
}

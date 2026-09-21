package com.elcafe.modules.promotion.repository;

import com.elcafe.modules.promotion.entity.HappyHourProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HappyHourProductRepository extends JpaRepository<HappyHourProduct, Long> {

    List<HappyHourProduct> findByHappyHourId(Long happyHourId);

    void deleteByHappyHourId(Long happyHourId);

    boolean existsByHappyHourIdAndProductId(Long happyHourId, Long productId);

    boolean existsByHappyHourIdAndCategoryId(Long happyHourId, Long categoryId);
}

package com.elcafe.modules.promotion.repository;

import com.elcafe.modules.promotion.entity.PromotionProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromotionProductRepository extends JpaRepository<PromotionProduct, Long> {

    List<PromotionProduct> findByPromotion_Id(Long promotionId);

    void deleteByPromotion_Id(Long promotionId);

    @Query("SELECT pp FROM PromotionProduct pp WHERE pp.promotion.id = :promotionId " +
           "AND pp.product.id = :productId AND pp.include = true")
    List<PromotionProduct> findIncludedByPromotionAndProduct(
            @Param("promotionId") Long promotionId,
            @Param("productId") Long productId);

    @Query("SELECT pp FROM PromotionProduct pp WHERE pp.promotion.id = :promotionId " +
           "AND pp.category.id = :categoryId AND pp.include = true")
    List<PromotionProduct> findIncludedByPromotionAndCategory(
            @Param("promotionId") Long promotionId,
            @Param("categoryId") Long categoryId);

    @Query("SELECT CASE WHEN COUNT(pp) > 0 THEN true ELSE false END FROM PromotionProduct pp " +
           "WHERE pp.promotion.id = :promotionId AND pp.product.id = :productId AND pp.include = false")
    boolean isProductExcluded(@Param("promotionId") Long promotionId, @Param("productId") Long productId);
}

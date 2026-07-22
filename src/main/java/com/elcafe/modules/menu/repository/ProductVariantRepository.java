package com.elcafe.modules.menu.repository;

import com.elcafe.modules.menu.entity.ProductVariant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ProductVariant entity
 */
@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductId(Long productId);

    Page<ProductVariant> findByProductId(Long productId, Pageable pageable);

    Optional<ProductVariant> findByIdAndProductId(Long id, Long productId);

    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id = :productId AND pv.inStock = :inStock")
    List<ProductVariant> findByProductIdAndInStock(@Param("productId") Long productId, @Param("inStock") Boolean inStock);

    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id = :productId AND " +
           "(LOWER(pv.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(pv.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ProductVariant> searchVariantsByProduct(
            @Param("productId") Long productId,
            @Param("search") String search,
            Pageable pageable
    );

    boolean existsByProductIdAndName(Long productId, String name);

    long countByProductId(Long productId);

    // Barcode/SKU lookup methods
    @Query("SELECT pv FROM ProductVariant pv JOIN pv.product p JOIN p.category c " +
           "WHERE c.restaurant.id = :restaurantId AND pv.barcode = :barcode")
    Optional<ProductVariant> findByProductRestaurantIdAndBarcode(@Param("restaurantId") Long restaurantId, @Param("barcode") String barcode);

    @Query("SELECT pv FROM ProductVariant pv JOIN pv.product p JOIN p.category c " +
           "WHERE c.restaurant.id = :restaurantId AND pv.sku = :sku")
    Optional<ProductVariant> findByProductRestaurantIdAndSku(@Param("restaurantId") Long restaurantId, @Param("sku") String sku);

    @Query("SELECT CASE WHEN COUNT(pv) > 0 THEN true ELSE false END FROM ProductVariant pv JOIN pv.product p JOIN p.category c " +
           "WHERE c.restaurant.id = :restaurantId AND (pv.barcode = :code OR pv.sku = :code)")
    boolean existsByProductRestaurantIdAndBarcodeOrSku(@Param("restaurantId") Long restaurantId, @Param("code") String code);
}

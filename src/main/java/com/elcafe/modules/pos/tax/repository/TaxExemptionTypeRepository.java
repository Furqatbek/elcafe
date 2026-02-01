package com.elcafe.modules.pos.tax.repository;

import com.elcafe.modules.pos.tax.entity.TaxExemptionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxExemptionTypeRepository extends JpaRepository<TaxExemptionType, Long> {

    List<TaxExemptionType> findByRestaurantIdAndIsActiveTrue(Long restaurantId);

    Optional<TaxExemptionType> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByRestaurantIdAndName(Long restaurantId, String name);
}

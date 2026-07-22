package com.elcafe.modules.settings.repository;

import com.elcafe.modules.settings.entity.ReceiptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReceiptTemplateRepository extends JpaRepository<ReceiptTemplate, Long> {
    Optional<ReceiptTemplate> findByRestaurantId(Long restaurantId);
}

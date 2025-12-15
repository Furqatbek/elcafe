package com.elcafe.modules.courier.repository;

import com.elcafe.modules.courier.entity.CourierTariff;
import com.elcafe.modules.courier.enums.TariffType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourierTariffRepository extends JpaRepository<CourierTariff, Long> {

    /**
     * Find all active tariffs
     */
    List<CourierTariff> findByActiveTrue();

    /**
     * Find all tariffs by type
     */
    Page<CourierTariff> findByType(TariffType type, Pageable pageable);

    /**
     * Find active tariffs by type
     */
    List<CourierTariff> findByTypeAndActiveTrue(TariffType type);

    /**
     * Find by name (case-insensitive)
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Find by name excluding a specific ID (for update validation)
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}

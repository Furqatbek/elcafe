package com.elcafe.modules.menu.repository;

import com.elcafe.modules.menu.entity.AddOn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddOnRepository extends JpaRepository<AddOn, Long> {

    List<AddOn> findByAddOnGroupIdOrderBySortOrder(Long addOnGroupId);

    List<AddOn> findByAddOnGroupIdAndAvailableTrueOrderBySortOrder(Long addOnGroupId);

    Optional<AddOn> findByIdAndAddOnGroupId(Long id, Long addOnGroupId);

    boolean existsByAddOnGroupIdAndName(Long addOnGroupId, String name);
}

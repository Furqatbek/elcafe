package com.elcafe.modules.bundle.repository;

import com.elcafe.modules.bundle.entity.BundleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BundleItemRepository extends JpaRepository<BundleItem, Long> {

    List<BundleItem> findByBundleId(Long bundleId);

    void deleteByBundleId(Long bundleId);
}

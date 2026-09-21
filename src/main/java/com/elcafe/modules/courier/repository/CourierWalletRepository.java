package com.elcafe.modules.courier.repository;

import com.elcafe.modules.courier.entity.CourierWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourierWalletRepository extends JpaRepository<CourierWallet, Long> {

    Optional<CourierWallet> findByCourierProfileId(Long courierProfileId);

    boolean existsByCourierProfileId(Long courierProfileId);
}

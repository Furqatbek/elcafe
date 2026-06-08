package com.elcafe.modules.loyalty.repository;

import com.elcafe.modules.loyalty.entity.WalletTopUp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletTopUpRepository extends JpaRepository<WalletTopUp, Long> {

    Optional<WalletTopUp> findByIdempotencyKey(String idempotencyKey);

    Optional<WalletTopUp> findByProviderAndExternalTransactionId(
            WalletTopUp.Provider provider, String externalTransactionId);

    Page<WalletTopUp> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<WalletTopUp> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<WalletTopUp> findByStatusOrderByCreatedAtDesc(WalletTopUp.Status status, Pageable pageable);
}

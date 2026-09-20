package uz.megahotdog.modules.loyalty.repository;

import uz.megahotdog.modules.loyalty.entity.TierHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TierHistoryRepository extends JpaRepository<TierHistory, Long> {

    List<TierHistory> findByCustomerLoyaltyIdOrderByCreatedAtDesc(Long customerLoyaltyId);

    Page<TierHistory> findByCustomerLoyaltyIdOrderByCreatedAtDesc(Long customerLoyaltyId, Pageable pageable);
}

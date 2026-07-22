package com.elcafe.modules.pos.display.repository;

import com.elcafe.modules.pos.display.entity.CustomerDisplayMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerDisplayMessageRepository extends JpaRepository<CustomerDisplayMessage, Long> {

    @Query("SELECT m FROM CustomerDisplayMessage m WHERE m.customerDisplay.id = :displayId " +
           "AND m.displayedAt IS NULL ORDER BY m.createdAt ASC")
    List<CustomerDisplayMessage> findPendingMessages(@Param("displayId") Long displayId);

    List<CustomerDisplayMessage> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    void deleteByCustomerDisplayIdAndDisplayedAtIsNotNull(Long displayId);
}

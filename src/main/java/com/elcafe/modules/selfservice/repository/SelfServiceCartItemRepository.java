package com.elcafe.modules.selfservice.repository;

import com.elcafe.modules.selfservice.entity.SelfServiceCartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SelfServiceCartItemRepository extends JpaRepository<SelfServiceCartItem, Long> {

    List<SelfServiceCartItem> findBySessionIdOrderByAddedAtAsc(Long sessionId);

    Optional<SelfServiceCartItem> findBySessionIdAndProductIdAndVariantId(
            Long sessionId, Long productId, Long variantId);

    @Query("SELECT SUM(c.quantity) FROM SelfServiceCartItem c WHERE c.session.id = :sessionId")
    Integer countItemsInCart(@Param("sessionId") Long sessionId);

    @Modifying
    @Query("DELETE FROM SelfServiceCartItem c WHERE c.session.id = :sessionId")
    void deleteAllBySessionId(@Param("sessionId") Long sessionId);
}

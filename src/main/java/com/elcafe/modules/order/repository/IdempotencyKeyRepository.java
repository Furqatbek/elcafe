package com.elcafe.modules.order.repository;

import com.elcafe.modules.order.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByKey(String key);

    @Query("SELECT i FROM IdempotencyKey i WHERE i.key = :key AND i.operationType = :operationType")
    Optional<IdempotencyKey> findByKeyAndOperationType(
            @Param("key") String key,
            @Param("operationType") String operationType);

    @Modifying
    @Query("DELETE FROM IdempotencyKey i WHERE i.expiresAt < :now")
    int deleteExpiredKeys(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(i) > 0 FROM IdempotencyKey i WHERE i.key = :key AND i.expiresAt > :now")
    boolean existsByKeyAndNotExpired(@Param("key") String key, @Param("now") LocalDateTime now);
}

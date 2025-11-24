package com.elcafe.modules.auth.repository;

import com.elcafe.modules.auth.entity.ConsumerSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for consumer session operations
 */
@Repository
public interface ConsumerSessionRepository extends JpaRepository<ConsumerSession, Long> {

    /**
     * Find session by session token
     */
    Optional<ConsumerSession> findBySessionTokenAndIsActiveTrue(String sessionToken);

    /**
     * Find session by refresh token
     */
    Optional<ConsumerSession> findByRefreshTokenAndIsActiveTrue(String refreshToken);

    /**
     * Find active sessions for a phone number
     */
    List<ConsumerSession> findByPhoneNumberAndIsActiveTrue(String phoneNumber);

    /**
     * Invalidate all sessions for a phone number
     */
    @Modifying
    @Query("UPDATE ConsumerSession s SET s.isActive = false WHERE s.phoneNumber = :phoneNumber")
    void invalidateAllSessionsByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    /**
     * Delete expired sessions (cleanup)
     */
    @Modifying
    @Query("DELETE FROM ConsumerSession s WHERE s.expiresAt < :now OR s.refreshExpiresAt < :now")
    void deleteExpiredSessions(@Param("now") LocalDateTime now);

    /**
     * Count active sessions for a phone number
     */
    long countByPhoneNumberAndIsActiveTrueAndExpiresAtAfter(
            String phoneNumber,
            LocalDateTime now
    );
}

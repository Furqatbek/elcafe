package com.elcafe.modules.auth.repository;

import com.elcafe.modules.auth.entity.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for OTP code operations
 */
@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    /**
     * Find the most recent unverified OTP for a phone number
     */
    @Query("SELECT o FROM OtpCode o WHERE o.phoneNumber = :phoneNumber " +
           "AND o.isVerified = false AND o.expiresAt > :now " +
           "ORDER BY o.createdAt DESC")
    Optional<OtpCode> findLatestValidOtp(
            @Param("phoneNumber") String phoneNumber,
            @Param("now") LocalDateTime now
    );

    /**
     * Find OTP by phone number and code
     */
    Optional<OtpCode> findByPhoneNumberAndOtpCodeAndIsVerifiedFalse(
            String phoneNumber,
            String otpCode
    );

    /**
     * Delete expired OTP codes (cleanup)
     */
    @Modifying
    @Query("DELETE FROM OtpCode o WHERE o.expiresAt < :now")
    void deleteExpiredOtps(@Param("now") LocalDateTime now);

    /**
     * Count recent OTP requests for rate limiting
     */
    @Query("SELECT COUNT(o) FROM OtpCode o WHERE o.phoneNumber = :phoneNumber " +
           "AND o.createdAt > :since")
    long countRecentOtpsByPhoneNumber(
            @Param("phoneNumber") String phoneNumber,
            @Param("since") LocalDateTime since
    );
}

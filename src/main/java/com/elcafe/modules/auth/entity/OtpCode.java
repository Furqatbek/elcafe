package com.elcafe.modules.auth.entity;

import com.elcafe.modules.customer.enums.RegistrationSource;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing an OTP code for phone-based authentication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "otp_codes", indexes = {
        @Index(name = "idx_otp_codes_phone_number", columnList = "phone_number"),
        @Index(name = "idx_otp_codes_expires_at", columnList = "expires_at"),
        @Index(name = "idx_otp_codes_is_verified", columnList = "is_verified")
})
public class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "otp_code", nullable = false, length = 6)
    private String otpCode;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    // Registration data (stored when requesting OTP)
    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_source", length = 50)
    private RegistrationSource registrationSource;

    @Column(name = "language", length = 10)
    private String language;

    /**
     * Check if OTP code is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if OTP can still be verified (not expired and not already verified)
     */
    public boolean canBeVerified() {
        return !isExpired() && !isVerified;
    }

    /**
     * Increment verification attempts
     */
    public void incrementAttempts() {
        this.attempts++;
    }
}

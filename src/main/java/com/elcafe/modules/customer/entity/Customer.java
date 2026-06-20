package com.elcafe.modules.customer.entity;

import com.elcafe.modules.customer.enums.RegistrationSource;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "customers")
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
// Phase 0: customers are tenant-scoped (V150). Safe to @Filter — after fragmentation every entity
// that references a customer does so within the same restaurant, so the §3.4 backstop scopes
// customer queries and lazy-loads without cross-tenant association fetches.
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tenant owner. Set from the resolved restaurant on creation: consumer login carries it
    // explicitly, and the order/reservation/self-service paths thread it from the order's restaurant.
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    // Uniqueness is per-restaurant (uq_customers_restaurant_email, V150), enforced at the DB level.
    @Column(length = 100)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "qr_code", nullable = false, unique = true, length = 40)
    private String qrCode;

    @Column(length = 500)
    private String defaultAddress;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 20)
    private String zipCode;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String tags;

    private LocalDate birthDate;

    @Column(length = 10)
    private String language; // e.g., "uz", "ru", "en"

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private RegistrationSource registrationSource;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    // Tax exemption fields
    @Column(name = "is_tax_exempt")
    @Builder.Default
    private Boolean isTaxExempt = false;

    @Column(name = "tax_exemption_type_id")
    private Long taxExemptionTypeId;

    @Column(name = "tax_exemption_number", length = 100)
    private String taxExemptionNumber;

    @Column(name = "tax_exemption_expires_at")
    private LocalDate taxExemptionExpiresAt;

    @CreatedDate
    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    @PrePersist
    private void ensureQrCode() {
        if (qrCode == null || qrCode.isBlank()) {
            qrCode = generateQrCode();
        }
    }

    public static String generateQrCode() {
        return "CST-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}

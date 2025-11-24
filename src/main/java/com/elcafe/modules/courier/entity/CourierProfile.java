package com.elcafe.modules.courier.entity;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.courier.enums.CourierType;
import com.elcafe.modules.courier.enums.CourierVehicle;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "courier_profiles")
@EntityListeners(AuditingEntityListener.class)
public class CourierProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CourierType courierType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CourierVehicle vehicle;

    @Column(length = 50)
    private String vehiclePlate;

    @Column(length = 100)
    private String licenseNumber;

    @Column(nullable = false)
    @Builder.Default
    private Boolean available = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean verified = false;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 20)
    private String emergencyContact;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

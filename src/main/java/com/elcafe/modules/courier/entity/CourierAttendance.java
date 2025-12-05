package com.elcafe.modules.courier.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "courier_attendance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"courier_profile_id", "date"}))
@EntityListeners(AuditingEntityListener.class)
public class CourierAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "courier_profile_id", nullable = false)
    private CourierProfile courierProfile;

    @Column(nullable = false)
    private LocalDate date;

    @Column
    private LocalTime checkInTime;

    @Column
    private LocalTime checkOutTime;

    @Column(nullable = false)
    @Builder.Default
    private Boolean present = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalTime createdAt;
}

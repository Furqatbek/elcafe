package com.elcafe.modules.promotion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(name = "happy_hour_schedules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HappyHourSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "happy_hour_id", nullable = false)
    private HappyHour happyHour;

    @Column(name = "day_of_week", nullable = false, length = 10)
    private String dayOfWeek; // MON, TUE, WED, THU, FRI, SAT, SUN

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}

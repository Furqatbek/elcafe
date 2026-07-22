package com.elcafe.modules.promotion.repository;

import com.elcafe.modules.promotion.entity.HappyHourSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HappyHourScheduleRepository extends JpaRepository<HappyHourSchedule, Long> {

    List<HappyHourSchedule> findByHappyHourId(Long happyHourId);

    void deleteByHappyHourId(Long happyHourId);
}

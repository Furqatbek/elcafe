package com.elcafe.modules.order.repository;

import com.elcafe.modules.order.entity.DailyOrderSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailyOrderSequenceRepository extends JpaRepository<DailyOrderSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DailyOrderSequence d WHERE d.date = :date")
    Optional<DailyOrderSequence> findByDateWithLock(@Param("date") LocalDate date);

    Optional<DailyOrderSequence> findByDate(LocalDate date);

    void deleteByDateBefore(LocalDate date);
}

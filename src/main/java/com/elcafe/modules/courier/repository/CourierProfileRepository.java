package com.elcafe.modules.courier.repository;

import com.elcafe.modules.courier.entity.CourierProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourierProfileRepository extends JpaRepository<CourierProfile, Long> {

    Optional<CourierProfile> findByUserId(Long userId);

    @Query("SELECT cp FROM CourierProfile cp JOIN cp.user u WHERE u.active = true")
    Page<CourierProfile> findAllActiveCouriers(Pageable pageable);

    boolean existsByUserId(Long userId);
}

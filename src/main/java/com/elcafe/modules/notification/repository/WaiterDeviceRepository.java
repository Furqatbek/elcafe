package com.elcafe.modules.notification.repository;

import com.elcafe.modules.notification.entity.WaiterDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaiterDeviceRepository extends JpaRepository<WaiterDevice, Long> {

    Optional<WaiterDevice> findByToken(String token);

    List<WaiterDevice> findByWaiterId(Long waiterId);
}

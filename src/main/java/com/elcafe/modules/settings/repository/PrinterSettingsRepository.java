package com.elcafe.modules.settings.repository;

import com.elcafe.modules.settings.entity.PrinterSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrinterSettingsRepository extends JpaRepository<PrinterSettings, Long> {

    List<PrinterSettings> findByRestaurant_Id(Long restaurantId);

    List<PrinterSettings> findByRestaurant_IdAndPrinterType(Long restaurantId, PrinterSettings.PrinterType printerType);

    Optional<PrinterSettings> findByRestaurant_IdAndPrinterTypeAndEnabled(
            Long restaurantId,
            PrinterSettings.PrinterType printerType,
            Boolean enabled
    );

    List<PrinterSettings> findByRestaurant_IdAndEnabled(Long restaurantId, Boolean enabled);
}

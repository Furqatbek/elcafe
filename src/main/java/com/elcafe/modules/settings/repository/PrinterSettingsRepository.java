package com.elcafe.modules.settings.repository;

import com.elcafe.modules.settings.entity.PrinterSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrinterSettingsRepository extends JpaRepository<PrinterSettings, Long> {

    List<PrinterSettings> findByRestaurantId(Long restaurantId);

    List<PrinterSettings> findByRestaurantIdAndPrinterType(Long restaurantId, PrinterSettings.PrinterType printerType);

    Optional<PrinterSettings> findByRestaurantIdAndPrinterTypeAndEnabled(
            Long restaurantId,
            PrinterSettings.PrinterType printerType,
            Boolean enabled
    );

    List<PrinterSettings> findByRestaurantIdAndEnabled(Long restaurantId, Boolean enabled);
}

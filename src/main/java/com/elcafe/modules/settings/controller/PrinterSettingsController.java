package com.elcafe.modules.settings.controller;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.settings.entity.PrinterSettings;
import com.elcafe.modules.settings.repository.PrinterSettingsRepository;
import com.elcafe.modules.settings.service.PrintService;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/settings/printers")
@RequiredArgsConstructor
public class PrinterSettingsController {

    private final PrinterSettingsRepository printerSettingsRepository;
    private final RestaurantRepository restaurantRepository;
    private final PrintService printService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PrinterSettings>>> getAllPrinters(
            @RequestParam Long restaurantId) {
        log.info("Getting printers for restaurant: {}", restaurantId);
        restaurantAuthorizationService.checkAccess(restaurantId);

        List<PrinterSettings> printers = printerSettingsRepository.findByRestaurant_Id(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Printers retrieved successfully", printers));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PrinterSettings>> getPrinterById(@PathVariable Long id) {
        log.info("Getting printer: {}", id);

        PrinterSettings printer = printerSettingsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Printer settings not found"));

        return ResponseEntity.ok(ApiResponse.success("Printer retrieved successfully", printer));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PrinterSettings>> createPrinter(
            @RequestBody PrinterSettings printerSettings) {
        log.info("Creating printer settings for restaurant: {}", printerSettings.getRestaurant().getId());
        restaurantAuthorizationService.checkAccess(printerSettings.getRestaurant().getId());

        Restaurant restaurant = restaurantRepository.findById(printerSettings.getRestaurant().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        printerSettings.setRestaurant(restaurant);
        PrinterSettings savedPrinter = printerSettingsRepository.save(printerSettings);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Printer created successfully", savedPrinter));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PrinterSettings>> updatePrinter(
            @PathVariable Long id,
            @RequestBody PrinterSettings printerSettings) {
        log.info("Updating printer: {}", id);

        PrinterSettings existingPrinter = printerSettingsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Printer settings not found"));

        existingPrinter.setPrinterType(printerSettings.getPrinterType());
        existingPrinter.setPrinterName(printerSettings.getPrinterName());
        existingPrinter.setIpAddress(printerSettings.getIpAddress());
        existingPrinter.setPort(printerSettings.getPort());
        existingPrinter.setConnectionType(printerSettings.getConnectionType());
        existingPrinter.setPaperWidth(printerSettings.getPaperWidth());
        existingPrinter.setFontSize(printerSettings.getFontSize());
        existingPrinter.setAutoPrint(printerSettings.getAutoPrint());
        existingPrinter.setEnabled(printerSettings.getEnabled());
        existingPrinter.setNotes(printerSettings.getNotes());

        PrinterSettings updatedPrinter = printerSettingsRepository.save(existingPrinter);
        return ResponseEntity.ok(ApiResponse.success("Printer updated successfully", updatedPrinter));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePrinter(@PathVariable Long id) {
        log.info("Deleting printer: {}", id);

        printerSettingsRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Printer deleted successfully", null));
    }

    @GetMapping("/available")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<String>>> getAvailablePrinters() {
        log.info("Getting available system printers");

        String[] printers = printService.getAvailablePrinters();
        return ResponseEntity.ok(ApiResponse.success(
                "Available printers retrieved successfully",
                Arrays.asList(printers)
        ));
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> testPrinter(@PathVariable Long id) {
        log.info("Testing printer: {}", id);

        boolean success = printService.testPrinter(id);
        String message = success ? "Printer test successful" : "Printer test failed";

        return ResponseEntity.ok(ApiResponse.success(message, success));
    }
}

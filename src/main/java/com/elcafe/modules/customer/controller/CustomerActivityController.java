package com.elcafe.modules.customer.controller;

import com.elcafe.modules.customer.dto.CustomerActivityDTO;
import com.elcafe.modules.customer.dto.CustomerActivityFilterDTO;
import com.elcafe.modules.customer.service.CustomerActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/customers/activity")
@RequiredArgsConstructor
public class CustomerActivityController {

    private final CustomerActivityService customerActivityService;

    /**
     * Get all customers with activity data (RFM analysis)
     * GET /api/customers/activity
     */
    @GetMapping
    public ResponseEntity<List<CustomerActivityDTO>> getAllCustomersActivity() {
        log.info("Getting all customers activity");
        List<CustomerActivityDTO> activities = customerActivityService.getAllCustomersActivity();
        return ResponseEntity.ok(activities);
    }

    /**
     * Get filtered customers with activity data
     * GET /api/customers/activity/filter
     */
    @GetMapping("/filter")
    public ResponseEntity<List<CustomerActivityDTO>> getFilteredCustomersActivity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) Integer minRecency,
            @RequestParam(required = false) Integer maxRecency,
            @RequestParam(required = false) Integer minFrequency,
            @RequestParam(required = false) Integer maxFrequency,
            @RequestParam(required = false) BigDecimal minMonetary,
            @RequestParam(required = false) BigDecimal maxMonetary,
            @RequestParam(required = false) String registrationSource,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String searchTerm
    ) {
        log.info("Getting filtered customers activity with filters: startDate={}, endDate={}, minRecency={}, maxRecency={}, " +
                        "minFrequency={}, maxFrequency={}, minMonetary={}, maxMonetary={}, registrationSource={}, active={}, searchTerm={}",
                startDate, endDate, minRecency, maxRecency, minFrequency, maxFrequency,
                minMonetary, maxMonetary, registrationSource, active, searchTerm);

        CustomerActivityFilterDTO filter = CustomerActivityFilterDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .minRecency(minRecency)
                .maxRecency(maxRecency)
                .minFrequency(minFrequency)
                .maxFrequency(maxFrequency)
                .minMonetary(minMonetary)
                .maxMonetary(maxMonetary)
                .registrationSource(registrationSource != null ?
                        com.elcafe.modules.customer.enums.RegistrationSource.valueOf(registrationSource) : null)
                .active(active)
                .searchTerm(searchTerm)
                .build();

        List<CustomerActivityDTO> activities = customerActivityService.getFilteredCustomersActivity(filter);
        return ResponseEntity.ok(activities);
    }

    /**
     * Alternative POST endpoint for complex filters
     * POST /api/customers/activity/filter
     */
    @PostMapping("/filter")
    public ResponseEntity<List<CustomerActivityDTO>> filterCustomersActivity(
            @RequestBody CustomerActivityFilterDTO filter
    ) {
        log.info("Filtering customers activity with filter: {}", filter);
        List<CustomerActivityDTO> activities = customerActivityService.getFilteredCustomersActivity(filter);
        return ResponseEntity.ok(activities);
    }
}

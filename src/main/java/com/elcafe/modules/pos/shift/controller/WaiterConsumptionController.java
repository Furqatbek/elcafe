package com.elcafe.modules.pos.shift.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.pos.shift.entity.EmployeeConsumption;
import com.elcafe.modules.pos.shift.repository.EmployeeConsumptionRepository;
import com.elcafe.modules.pos.shift.service.ConsumptionLimitService;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Mobile-app-facing consumption history for waiters. Returns a lean
 * projection (no expense / nested user blob) so the phone client can
 * render a list without paying for the full graph.
 */
@RestController
@RequestMapping("/api/v1/waiter/consumptions")
@RequiredArgsConstructor
public class WaiterConsumptionController {

    private final EmployeeConsumptionRepository consumptionRepository;
    private final ConsumptionLimitService consumptionLimitService;
    private final WaiterRepository waiterRepository;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('WAITER', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<WaiterConsumptionResponse>>> getMyConsumptions(
            @RequestHeader("X-Waiter-Id") Long waiterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        // Default to "today" when the client omits the range. The mobile
        // app's home tile typically shows just today; it can pass an
        // explicit range for the history screen.
        LocalDate today = LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : today;
        LocalDate effectiveTo = to != null ? to : today;

        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime start = effectiveFrom.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = effectiveTo.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        List<EmployeeConsumption> rows = consumptionRepository
                .findByWaiter_IdAndConsumedAtBetweenOrderByConsumedAtDesc(waiterId, start, end);

        List<WaiterConsumptionResponse> response = rows.stream()
                .map(WaiterConsumptionResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Consumptions retrieved", response));
    }

    /**
     * Quota status across every allowance applicable to this waiter — the
     * mobile app's home screen uses this to render "X of Y left today"
     * tiles without doing the math client-side. Waiters can serve at
     * more than one restaurant, so the client passes the restaurant id
     * they're currently working at.
     */
    @GetMapping("/quota")
    @PreAuthorize("hasAnyRole('WAITER', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<ConsumptionLimitService.QuotaStatus>>> getMyQuota(
            @RequestHeader("X-Waiter-Id") Long waiterId,
            @RequestParam Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new IllegalArgumentException("Waiter not found"));
        List<ConsumptionLimitService.QuotaStatus> statuses =
                consumptionLimitService.quotaStatusFor(restaurantId, null, waiter);
        return ResponseEntity.ok(ApiResponse.success("Quota retrieved", statuses));
    }

    /**
     * Lean projection of EmployeeConsumption for the mobile client.
     * Drops the expense reference, the nested employee/waiter blobs,
     * and the soft-delete plumbing.
     */
    public record WaiterConsumptionResponse(
            Long id,
            OffsetDateTime consumedAt,
            String productName,
            Integer quantity,
            BigDecimal totalCost,
            Boolean chargedToEmployee,
            BigDecimal chargedAmount,
            String notes
    ) {
        static WaiterConsumptionResponse from(EmployeeConsumption c) {
            return new WaiterConsumptionResponse(
                    c.getId(),
                    c.getConsumedAt(),
                    c.getProductName(),
                    c.getQuantity(),
                    c.getTotalCost(),
                    c.getChargedToEmployee(),
                    c.getChargedAmount(),
                    c.getNotes());
        }
    }
}

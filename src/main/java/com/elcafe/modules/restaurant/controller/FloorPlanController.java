package com.elcafe.modules.restaurant.controller;

import com.elcafe.modules.restaurant.dto.FloorLayoutRequest;
import com.elcafe.modules.restaurant.dto.FloorPlanView;
import com.elcafe.modules.restaurant.service.FloorPlanService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The live floor map (V184).
 *
 * <p><b>The role gate is deliberately split, and it is split here rather than in the UI.</b> Reading the
 * room is open to the restaurant's staff — a waiter, a host and a cashier all need to see who is sitting
 * where to do their jobs. Writing the layout is restricted to {@code OWNER} and {@code MANAGER}: moving
 * furniture is a decision about the restaurant, not a service action, and a stray drag on a tablet
 * during a busy Friday must not be able to rearrange the room for everyone.
 *
 * <p>Hiding the edit button in the frontend is presentation, not security — the write methods carry
 * their own {@code @PreAuthorize} so a hand-rolled request from a waiter's token is refused by the
 * server. {@code RbacGateAnnotationTest} pins both halves so neither can be silently widened.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/floor-plans")
@RequiredArgsConstructor
@Tag(name = "Floor Map", description = "Live restaurant floor map: layout, furniture, sections, occupancy")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'OPERATOR', 'SUPERVISOR', 'HEAD_WAITER', 'WAITER', 'CASHIER')")
public class FloorPlanController {

    private final FloorPlanService floorPlanService;

    @GetMapping
    @Operation(summary = "List this restaurant's maps",
            description = "Names and canvas sizes only — the switcher does not need every table on every floor.")
    public ResponseEntity<ApiResponse<List<FloorPlanView>>> listPlans() {
        return ResponseEntity.ok(ApiResponse.success(floorPlanService.listPlans()));
    }

    @GetMapping("/default")
    @Operation(summary = "Open the default map",
            description = "The map with everything on it plus live occupancy, for a client with no remembered choice.")
    public ResponseEntity<ApiResponse<FloorPlanView>> getDefaultPlan() {
        return ResponseEntity.ok(ApiResponse.success(floorPlanService.getPlan(null)));
    }

    @GetMapping("/{planId}")
    @Operation(summary = "Open one map",
            description = "Sections, furniture, tables and who is sitting at each — everything the renderer needs.")
    public ResponseEntity<ApiResponse<FloorPlanView>> getPlan(@PathVariable Long planId) {
        return ResponseEntity.ok(ApiResponse.success(floorPlanService.getPlan(planId)));
    }

    /**
     * Saving the layout is a management action, so the gate narrows here — every staff role above may
     * read the map, only these two may change it.
     */
    @PutMapping("/{planId}/layout")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER')")
    @Operation(summary = "Save a map's layout",
            description = "One transaction for the whole edit session: moved tables, the full furniture "
                    + "list and the full section list. Owner/manager only.")
    public ResponseEntity<ApiResponse<FloorPlanView>> saveLayout(
            @PathVariable Long planId,
            @Valid @RequestBody FloorLayoutRequest request) {
        log.info("Saving floor plan {} layout", planId);
        return ResponseEntity.ok(ApiResponse.success(
                "Layout saved", floorPlanService.saveLayout(planId, request)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER')")
    @Operation(summary = "Add a map", description = "A new floor, hall or terrace. Owner/manager only.")
    public ResponseEntity<ApiResponse<FloorPlanView>> createPlan(@RequestBody FloorPlanView request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Map created", floorPlanService.createPlan(request)));
    }

    @PutMapping("/{planId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER')")
    @Operation(summary = "Rename or resize a map", description = "Owner/manager only.")
    public ResponseEntity<ApiResponse<FloorPlanView>> updatePlan(
            @PathVariable Long planId, @RequestBody FloorPlanView request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Map updated", floorPlanService.updatePlan(planId, request)));
    }

    @DeleteMapping("/{planId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER')")
    @Operation(summary = "Remove a map",
            description = "Its furniture and sections go with it; its tables survive, unplaced. Owner/manager only.")
    public ResponseEntity<ApiResponse<Void>> deletePlan(@PathVariable Long planId) {
        floorPlanService.deletePlan(planId);
        return ResponseEntity.ok(ApiResponse.success("Map removed", null));
    }
}

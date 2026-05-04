package com.elcafe.modules.pos.shift.controller;

import com.elcafe.modules.pos.shift.entity.ShiftRules;
import com.elcafe.modules.pos.shift.service.OvertimeRuleService;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shift-rules")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class ShiftRulesController {

    private final OvertimeRuleService overtimeRuleService;

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ApiResponse<ShiftRules>> getRules(@PathVariable Long restaurantId) {
        ShiftRules rules = overtimeRuleService.getOrCreateRules(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Shift rules retrieved", rules));
    }

    @PutMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ApiResponse<ShiftRules>> updateRules(
            @PathVariable Long restaurantId,
            @RequestBody ShiftRules rules) {
        ShiftRules updated = overtimeRuleService.updateRules(restaurantId, rules);
        return ResponseEntity.ok(ApiResponse.success("Shift rules updated", updated));
    }
}

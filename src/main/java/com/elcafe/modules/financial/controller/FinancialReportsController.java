package com.elcafe.modules.financial.controller;

import com.elcafe.modules.financial.service.FinancialReportsService;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/v1/financial/reports")
@RequiredArgsConstructor
public class FinancialReportsController {

    private final FinancialReportsService reportsService;

    @GetMapping("/profit-loss")
    public ResponseEntity<ApiResponse<FinancialReportsService.ProfitLossReport>> getProfitLossReport(
            @RequestParam Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Generating P&L report for restaurant: {} from {} to {}", restaurantId, startDate, endDate);

        FinancialReportsService.ProfitLossReport report =
                reportsService.generateProfitLossReport(restaurantId, startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success("Profit & Loss report generated successfully", report));
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<ApiResponse<FinancialReportsService.BalanceSheetReport>> getBalanceSheet(
            @RequestParam Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        log.info("Generating balance sheet for restaurant: {} as of {}", restaurantId, asOfDate);

        FinancialReportsService.BalanceSheetReport report =
                reportsService.generateBalanceSheet(restaurantId, asOfDate);

        return ResponseEntity.ok(ApiResponse.success("Balance sheet generated successfully", report));
    }

    @GetMapping("/cash-flow")
    public ResponseEntity<ApiResponse<FinancialReportsService.CashFlowReport>> getCashFlowReport(
            @RequestParam Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Generating cash flow report for restaurant: {} from {} to {}", restaurantId, startDate, endDate);

        FinancialReportsService.CashFlowReport report =
                reportsService.generateCashFlowReport(restaurantId, startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success("Cash flow report generated successfully", report));
    }

    @GetMapping("/cogs")
    public ResponseEntity<ApiResponse<FinancialReportsService.CogsReport>> getCogsReport(
            @RequestParam Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Generating COGS report for restaurant: {} from {} to {}", restaurantId, startDate, endDate);

        FinancialReportsService.CogsReport report =
                reportsService.generateCogsReport(restaurantId, startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success("COGS report generated successfully", report));
    }
}

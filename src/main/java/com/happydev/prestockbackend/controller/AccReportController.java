package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.FinancialStatementDto;
import com.happydev.prestockbackend.dto.LedgerEntryDto;
import com.happydev.prestockbackend.dto.TrialBalanceRowDto;
import com.happydev.prestockbackend.service.AccountingReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/accounting/reports")
public class AccReportController {

    private final AccountingReportService reportService;

    public AccReportController(AccountingReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/trial-balance")
    public ResponseEntity<List<TrialBalanceRowDto>> getTrialBalance(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LocalDate from = parseOrFirstOfMonth(startDate);
        LocalDate to   = parseOrLastOfMonth(endDate);

        return ResponseEntity.ok(reportService.getTrialBalance(from, to));
    }

    @GetMapping("/income-statement")
    public ResponseEntity<FinancialStatementDto> getIncomeStatement(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LocalDate from = parseOrFirstOfMonth(startDate);
        LocalDate to   = parseOrLastOfMonth(endDate);

        return ResponseEntity.ok(reportService.getIncomeStatement(from, to));
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<FinancialStatementDto> getBalanceSheet(
            @RequestParam(required = false) String asOfDate) {

        LocalDate date = asOfDate != null && !asOfDate.isBlank()
                ? LocalDate.parse(asOfDate)
                : LocalDate.now();

        return ResponseEntity.ok(reportService.getBalanceSheet(date));
    }

    @GetMapping("/ledger/{accountId}")
    public ResponseEntity<List<LedgerEntryDto>> getLedger(
            @PathVariable Long accountId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LocalDate from = parseOrFirstOfMonth(startDate);
        LocalDate to   = parseOrLastOfMonth(endDate);

        return ResponseEntity.ok(reportService.getLedger(accountId, from, to));
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private LocalDate parseOrFirstOfMonth(String value) {
        if (value != null && !value.isBlank()) {
            return LocalDate.parse(value);
        }
        return YearMonth.now().atDay(1);
    }

    private LocalDate parseOrLastOfMonth(String value) {
        if (value != null && !value.isBlank()) {
            return LocalDate.parse(value);
        }
        return YearMonth.now().atEndOfMonth();
    }
}

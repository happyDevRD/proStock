package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.FinancialStatementDto;
import com.happydev.prestockbackend.dto.LedgerEntryDto;
import com.happydev.prestockbackend.dto.TrialBalanceRowDto;
import com.happydev.prestockbackend.service.AccountingReportService;
import com.happydev.prestockbackend.service.AccountingExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/accounting/reports")
public class AccReportController {

    private final AccountingReportService reportService;
    private final AccountingExportService exportService;

    public AccReportController(AccountingReportService reportService,
                               AccountingExportService exportService) {
        this.reportService = reportService;
        this.exportService = exportService;
    }

    @GetMapping("/trial-balance")
    @PreAuthorize("hasAuthority('accounting.module.view')")
    public ResponseEntity<List<TrialBalanceRowDto>> getTrialBalance(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        return ResponseEntity.ok(reportService.getTrialBalance(
                parseOrFirstOfMonth(startDate), parseOrLastOfMonth(endDate)));
    }

    @GetMapping("/trial-balance/export")
    @PreAuthorize("hasAuthority('accounting.reports.export')")
    public ResponseEntity<byte[]> exportTrialBalance(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        byte[] bytes = exportService.exportTrialBalance(
                parseOrFirstOfMonth(startDate), parseOrLastOfMonth(endDate));
        return xlsxResponse(bytes, "balanza_comprobacion.xlsx");
    }

    @GetMapping("/income-statement")
    @PreAuthorize("hasAuthority('accounting.module.view')")
    public ResponseEntity<FinancialStatementDto> getIncomeStatement(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        return ResponseEntity.ok(reportService.getIncomeStatement(
                parseOrFirstOfMonth(startDate), parseOrLastOfMonth(endDate)));
    }

    @GetMapping("/income-statement/export")
    @PreAuthorize("hasAuthority('accounting.reports.export')")
    public ResponseEntity<byte[]> exportIncomeStatement(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        byte[] bytes = exportService.exportIncomeStatement(
                parseOrFirstOfMonth(startDate), parseOrLastOfMonth(endDate));
        return xlsxResponse(bytes, "estado_resultados.xlsx");
    }

    @GetMapping("/balance-sheet")
    @PreAuthorize("hasAuthority('accounting.module.view')")
    public ResponseEntity<FinancialStatementDto> getBalanceSheet(
            @RequestParam(required = false) String asOfDate) {

        LocalDate date = asOfDate != null && !asOfDate.isBlank()
                ? LocalDate.parse(asOfDate) : LocalDate.now();
        return ResponseEntity.ok(reportService.getBalanceSheet(date));
    }

    @GetMapping("/balance-sheet/export")
    @PreAuthorize("hasAuthority('accounting.reports.export')")
    public ResponseEntity<byte[]> exportBalanceSheet(
            @RequestParam(required = false) String asOfDate) {

        LocalDate date = asOfDate != null && !asOfDate.isBlank()
                ? LocalDate.parse(asOfDate) : LocalDate.now();
        byte[] bytes = exportService.exportBalanceSheet(date);
        return xlsxResponse(bytes, "balance_general.xlsx");
    }

    @GetMapping("/ledger/{accountId}")
    @PreAuthorize("hasAuthority('accounting.module.view')")
    public ResponseEntity<List<LedgerEntryDto>> getLedger(
            @PathVariable Long accountId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        return ResponseEntity.ok(reportService.getLedger(accountId,
                parseOrFirstOfMonth(startDate), parseOrLastOfMonth(endDate)));
    }

    @GetMapping("/ledger/{accountId}/export")
    @PreAuthorize("hasAuthority('accounting.reports.export')")
    public ResponseEntity<byte[]> exportLedger(
            @PathVariable Long accountId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        byte[] bytes = exportService.exportLedger(accountId,
                parseOrFirstOfMonth(startDate), parseOrLastOfMonth(endDate));
        return xlsxResponse(bytes, "libro_mayor.xlsx");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private LocalDate parseOrFirstOfMonth(String value) {
        if (value != null && !value.isBlank()) return LocalDate.parse(value);
        return YearMonth.now().atDay(1);
    }

    private LocalDate parseOrLastOfMonth(String value) {
        if (value != null && !value.isBlank()) return LocalDate.parse(value);
        return YearMonth.now().atEndOfMonth();
    }

    private ResponseEntity<byte[]> xlsxResponse(byte[] bytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}

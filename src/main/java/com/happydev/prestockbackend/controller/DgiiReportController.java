package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.Dgii607ReportDto;
import com.happydev.prestockbackend.dto.Dgii608ReportDto;
import com.happydev.prestockbackend.service.DgiiReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/reports/dgii")
@PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'view.reports')")
public class DgiiReportController {

    private static final DateTimeFormatter PERIODO_DGII = DateTimeFormatter.ofPattern("yyyyMM");

    private final DgiiReportService dgiiReportService;

    public DgiiReportController(DgiiReportService dgiiReportService) {
        this.dgiiReportService = dgiiReportService;
    }

    @GetMapping("/607")
    public ResponseEntity<Dgii607ReportDto> get607(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth period) {
        return ResponseEntity.ok(dgiiReportService.build607(period));
    }

    @GetMapping("/607/txt")
    public ResponseEntity<byte[]> download607Txt(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth period) {
        return txtDownload("607", period, dgiiReportService.render607Txt(period));
    }

    @GetMapping("/608")
    public ResponseEntity<Dgii608ReportDto> get608(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth period) {
        return ResponseEntity.ok(dgiiReportService.build608(period));
    }

    @GetMapping("/608/txt")
    public ResponseEntity<byte[]> download608Txt(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth period) {
        return txtDownload("608", period, dgiiReportService.render608Txt(period));
    }

    private ResponseEntity<byte[]> txtDownload(String tipo, YearMonth period, String content) {
        String filename = "DGII_" + tipo + "_" + period.format(PERIODO_DGII) + ".txt";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}

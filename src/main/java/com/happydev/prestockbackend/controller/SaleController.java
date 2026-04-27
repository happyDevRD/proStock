package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.service.SaleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping
    public ResponseEntity<List<SaleDto>> getAllSales() {
        List<SaleDto> sales = saleService.findAllSales();
        return new ResponseEntity<>(sales, HttpStatus.OK);
    }

    @GetMapping("/paginated")
    public ResponseEntity<Page<SaleDto>> getAllSales(Pageable pageable) {
        Page<SaleDto> sales = saleService.findAllSales(pageable);
        return new ResponseEntity<>(sales, HttpStatus.OK);
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) SaleStatus status
    ) {
        List<SaleDto> filtered = saleService.findAllSales().stream()
                .filter(sale -> status == null || sale.getStatus() == status)
                .filter(sale -> startDate == null || !sale.getSaleDate().isBefore(startDate))
                .filter(sale -> endDate == null || !sale.getSaleDate().isAfter(endDate))
                .toList();

        String header = "id,saleDate,status,tipoComprobante,ncf,montoTotal,totalItbis";
        String body = filtered.stream()
                .map(sale -> String.join(",",
                        String.valueOf(sale.getId()),
                        sale.getSaleDate() != null ? sale.getSaleDate().toString() : "",
                        sale.getStatus() != null ? sale.getStatus().name() : "",
                        sale.getTipoComprobante() != null ? sale.getTipoComprobante() : "",
                        sale.getNcf() != null ? sale.getNcf() : "",
                        sale.getMontoTotal() != null ? sale.getMontoTotal().toPlainString() : "0",
                        sale.getTotalItbis() != null ? sale.getTotalItbis().toPlainString() : "0"
                ))
                .collect(Collectors.joining("\n"));

        String csv = header + "\n" + body;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sales-export.csv")
                .contentType(new MediaType("text", "csv"))
                .body(csv);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SaleDto> getSaleById(@PathVariable Long id) {
        return saleService.findSaleById(id)
                .map(sale -> new ResponseEntity<>(sale, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<SaleDto> createSale(@Valid @RequestBody SaleDto saleDto) {
        SaleDto savedSale = saleService.createSale(saleDto);
        return new ResponseEntity<>(savedSale, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SaleDto> updateSale(@PathVariable Long id, @Valid @RequestBody SaleDto saleDto) {
        SaleDto updatedSale = saleService.updateSale(id, saleDto);
        return new ResponseEntity<>(updatedSale, HttpStatus.OK);
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSale(@PathVariable Long id) {
        saleService.deleteSale(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PutMapping("/{id}/complete") // Endpoint para completar una venta (y descontar stock)
    public ResponseEntity<SaleDto> completeSale(@PathVariable Long id) {
        SaleDto completedSale = saleService.completeSale(id);
        return new ResponseEntity<>(completedSale, HttpStatus.OK);
    }
}

package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.service.SaleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final SaleService saleService;

    public InvoiceController(SaleService saleService) {
        this.saleService = saleService;
    }

    /**
     * Listado paginado de facturas (ventas completadas con NCF), con filtros opcionales.
     */
    @GetMapping
    public ResponseEntity<Page<SaleDto>> listInvoices(
            @RequestParam(required = false) String ncf,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean overdueOnly,
            @NonNull Pageable pageable
    ) {
        Page<SaleDto> page = saleService.findInvoices(pageable, ncf, customerId, dateFrom, dateTo, status, overdueOnly);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/by-ncf/{ncf}")
    public ResponseEntity<SaleDto> getByNcf(@PathVariable @NonNull String ncf) {
        return saleService.findSaleByNcf(ncf)
                .map(ResponseEntity::ok)
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}

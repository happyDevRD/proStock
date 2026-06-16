package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.CreateStockTransferRequest;
import com.happydev.prestockbackend.dto.StockTransferDto;
import com.happydev.prestockbackend.service.StockTransferService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stock-transfers")
public class StockTransferController {

    private final StockTransferService transferService;

    public StockTransferController(StockTransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'locations.view')")
    public ResponseEntity<Page<StockTransferDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(transferService.list(page, size, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'locations.view')")
    public ResponseEntity<StockTransferDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(transferService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'locations.transfer')")
    public ResponseEntity<StockTransferDto> create(@Valid @RequestBody CreateStockTransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.create(request));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'locations.transfer')")
    public ResponseEntity<StockTransferDto> complete(@PathVariable Long id) {
        return ResponseEntity.ok(transferService.complete(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'locations.transfer')")
    public ResponseEntity<StockTransferDto> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(transferService.cancel(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'locations.transfer')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        transferService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

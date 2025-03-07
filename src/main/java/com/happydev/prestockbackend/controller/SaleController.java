package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.service.SaleService;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales")
@CrossOrigin(origins = "*") // Configura CORS adecuadamente en producción
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

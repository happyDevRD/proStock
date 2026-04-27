package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.StockMovementDto;
import com.happydev.prestockbackend.entity.StockMovement;
import com.happydev.prestockbackend.entity.StockMovementType;
import com.happydev.prestockbackend.service.StockMovementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock-movements")
public class StockMovementController {

    private final StockMovementService stockMovementService;

    public StockMovementController(StockMovementService stockMovementService) {
        this.stockMovementService = stockMovementService;
    }

    @GetMapping
    public ResponseEntity<Page<StockMovementDto>> getAllMovements(Pageable pageable) {
        Page<StockMovementDto> movements = stockMovementService.getAllMovements(pageable).map(this::toDto);
        return new ResponseEntity<>(movements, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StockMovementDto> getMovementById(@PathVariable Long id) {
        return stockMovementService.getMovementById(id)
                .map(movement -> new ResponseEntity<>(toDto(movement), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<StockMovementDto>> getMovementsByProduct(@PathVariable Long productId) {
        List<StockMovementDto> movements = stockMovementService.getMovementsByProduct(productId)
                .stream()
                .map(this::toDto)
                .toList();
        return new ResponseEntity<>(movements, HttpStatus.OK);
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<StockMovementDto>> getMovementsByType(@PathVariable StockMovementType type) {
        List<StockMovementDto> movements = stockMovementService.getMovementsByType(type)
                .stream()
                .map(this::toDto)
                .toList();
        return new ResponseEntity<>(movements, HttpStatus.OK);
    }

    @GetMapping("/sale/{saleId}")
    public ResponseEntity<List<StockMovementDto>> getMovementsBySale(@PathVariable Long saleId) {
        List<StockMovementDto> movements = stockMovementService.getMovementsBySale(saleId)
                .stream()
                .map(this::toDto)
                .toList();
        return new ResponseEntity<>(movements, HttpStatus.OK);
    }

    @GetMapping("/purchase-order/{purchaseOrderId}")
    public ResponseEntity<List<StockMovementDto>> getMovementsByPurchaseOrder(@PathVariable Long purchaseOrderId) {
        List<StockMovementDto> movements = stockMovementService.getMovementsByPurchaseOrder(purchaseOrderId)
                .stream()
                .map(this::toDto)
                .toList();
        return new ResponseEntity<>(movements, HttpStatus.OK);
    }

    private StockMovementDto toDto(StockMovement movement) {
        StockMovementDto dto = new StockMovementDto();
        dto.setId(movement.getId());
        dto.setProductId(movement.getProduct() != null ? movement.getProduct().getId() : null);
        dto.setMovementDate(movement.getMovementDate());
        dto.setQuantityChange(movement.getQuantityChange());
        dto.setType(movement.getType());
        dto.setReason(movement.getReason());
        dto.setPurchaseOrderId(movement.getPurchaseOrder() != null ? movement.getPurchaseOrder().getId() : null);
        dto.setSaleId(movement.getSale() != null ? movement.getSale().getId() : null);
        dto.setUserId(movement.getUser() != null ? movement.getUser().getId() : null);
        dto.setSourceLocationId(movement.getSourceLocationId());
        dto.setDestinationLocationId(movement.getDestinationLocationId());
        dto.setBatchNumber(movement.getBatchNumber());
        dto.setExpirationDate(movement.getExpirationDate());
        dto.setUnitCost(movement.getUnitCost());
        dto.setStockBefore(movement.getStockBefore());
        dto.setStockAfter(movement.getStockAfter());
        return dto;
    }
}

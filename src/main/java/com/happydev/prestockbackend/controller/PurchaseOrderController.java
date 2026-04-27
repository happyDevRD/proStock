package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.PurchaseOrderDto;
import com.happydev.prestockbackend.service.PurchaseOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping
    public ResponseEntity<List<PurchaseOrderDto>> getAllPurchaseOrders() {
        List<PurchaseOrderDto> orders = purchaseOrderService.findAllPurchaseOrders();
        return new ResponseEntity<>(orders, HttpStatus.OK);
    }

    @GetMapping("/paginated") // Endpoint separado para la paginación
    public ResponseEntity<Page<PurchaseOrderDto>> getAllPurchaseOrders(Pageable pageable) {
        Page<PurchaseOrderDto> orders = purchaseOrderService.findAllPurchaseOrders(pageable);
        return new ResponseEntity<>(orders, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrderDto> getPurchaseOrderById(@PathVariable Long id) {
        return purchaseOrderService.findPurchaseOrderById(id)
                .map(order -> new ResponseEntity<>(order, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<PurchaseOrderDto> createPurchaseOrder(@Valid @RequestBody PurchaseOrderDto purchaseOrderDto) {
        PurchaseOrderDto savedOrder = purchaseOrderService.createPurchaseOrder(purchaseOrderDto);
        return new ResponseEntity<>(savedOrder, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PurchaseOrderDto> updatePurchaseOrder(@PathVariable Long id, @Valid @RequestBody PurchaseOrderDto purchaseOrderDto) {
        PurchaseOrderDto updatedOrder = purchaseOrderService.updatePurchaseOrder(id, purchaseOrderDto);
        return new ResponseEntity<>(updatedOrder, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePurchaseOrder(@PathVariable Long id) {
        purchaseOrderService.deletePurchaseOrder(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PutMapping("/{id}/receive") // Endpoint para recibir una orden
    public ResponseEntity<PurchaseOrderDto> receivePurchaseOrder(@PathVariable Long id) {
        PurchaseOrderDto receivedOrder = purchaseOrderService.receivePurchaseOrder(id);
        return new ResponseEntity<>(receivedOrder, HttpStatus.OK);
    }
}


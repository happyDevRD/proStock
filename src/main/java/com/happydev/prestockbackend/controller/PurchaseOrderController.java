package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.AddPaymentRequest;
import com.happydev.prestockbackend.dto.PurchaseOrderDto;
import com.happydev.prestockbackend.dto.SupplierPaymentDto;
import com.happydev.prestockbackend.service.PurchaseOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping("/payable")
    public ResponseEntity<List<PurchaseOrderDto>> getPayableOrders() {
        return ResponseEntity.ok(purchaseOrderService.findPayableOrders());
    }

    @GetMapping
    public ResponseEntity<List<PurchaseOrderDto>> getAllPurchaseOrders() {
        return ResponseEntity.ok(purchaseOrderService.findAllPurchaseOrders());
    }

    @GetMapping("/paginated")
    public ResponseEntity<Page<PurchaseOrderDto>> getAllPurchaseOrders(@NonNull Pageable pageable) {
        return ResponseEntity.ok(purchaseOrderService.findAllPurchaseOrders(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrderDto> getPurchaseOrderById(@PathVariable @NonNull Long id) {
        return purchaseOrderService.findPurchaseOrderById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PurchaseOrderDto> createPurchaseOrder(@Valid @RequestBody @NonNull PurchaseOrderDto dto) {
        return new ResponseEntity<>(purchaseOrderService.createPurchaseOrder(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PurchaseOrderDto> updatePurchaseOrder(@PathVariable @NonNull Long id,
                                                                 @Valid @RequestBody @NonNull PurchaseOrderDto dto) {
        return ResponseEntity.ok(purchaseOrderService.updatePurchaseOrder(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePurchaseOrder(@PathVariable @NonNull Long id) {
        purchaseOrderService.deletePurchaseOrder(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/receive")
    public ResponseEntity<PurchaseOrderDto> receivePurchaseOrder(@PathVariable @NonNull Long id) {
        return ResponseEntity.ok(purchaseOrderService.receivePurchaseOrder(id));
    }

    // -------------------------------------------------------
    // Payment endpoints
    // -------------------------------------------------------

    @GetMapping("/{id}/payments")
    public ResponseEntity<List<SupplierPaymentDto>> getPayments(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.getPaymentsForPurchaseOrder(id));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<SupplierPaymentDto> addPayment(@PathVariable Long id,
                                                          @Valid @RequestBody AddPaymentRequest req,
                                                          Principal principal) {
        String actor = principal != null ? principal.getName() : null;
        SupplierPaymentDto dto = purchaseOrderService.addPayment(
                id, req.getAmount(), req.getPaymentMethod(), req.getNotes(), actor);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}/payments/{paymentId}")
    public ResponseEntity<PurchaseOrderDto> voidPayment(@PathVariable Long id,
                                                         @PathVariable Long paymentId,
                                                         Principal principal) {
        String actor = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(purchaseOrderService.voidPayment(id, paymentId, actor));
    }
}

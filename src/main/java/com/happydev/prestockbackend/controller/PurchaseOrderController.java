package com.happydev.prestockbackend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.happydev.prestockbackend.security.Authz;

import com.happydev.prestockbackend.dto.AddPaymentRequest;
import com.happydev.prestockbackend.dto.PurchaseOrderDto;
import com.happydev.prestockbackend.dto.ReceivePurchaseOrderRequest;
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

    @PreAuthorize(Authz.PAYABLES)
    @GetMapping("/payable")
    public ResponseEntity<List<PurchaseOrderDto>> getPayableOrders() {
        return ResponseEntity.ok(purchaseOrderService.findPayableOrders());
    }

    @PreAuthorize(Authz.PURCHASE_READ)
    @GetMapping
    public ResponseEntity<List<PurchaseOrderDto>> getAllPurchaseOrders() {
        return ResponseEntity.ok(purchaseOrderService.findAllPurchaseOrders());
    }

    /** Todas las órdenes por recibir (pendientes o recibidas en parte), sin límite de página. */
    @PreAuthorize(Authz.PURCHASE_READ)
    @GetMapping("/receivable")
    public ResponseEntity<List<PurchaseOrderDto>> getReceivableOrders() {
        return ResponseEntity.ok(purchaseOrderService.findReceivableOrders());
    }

    @PreAuthorize(Authz.PURCHASE_READ)
    @GetMapping("/paginated")
    public ResponseEntity<Page<PurchaseOrderDto>> getAllPurchaseOrders(@NonNull Pageable pageable) {
        return ResponseEntity.ok(purchaseOrderService.findAllPurchaseOrders(pageable));
    }

    @PreAuthorize(Authz.PURCHASE_READ)
    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrderDto> getPurchaseOrderById(@PathVariable @NonNull Long id) {
        return purchaseOrderService.findPurchaseOrderById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize(Authz.PURCHASE_EDIT)
    @PostMapping
    public ResponseEntity<PurchaseOrderDto> createPurchaseOrder(@Valid @RequestBody @NonNull PurchaseOrderDto dto) {
        return new ResponseEntity<>(purchaseOrderService.createPurchaseOrder(dto), HttpStatus.CREATED);
    }

    @PreAuthorize(Authz.PURCHASE_EDIT)
    @PutMapping("/{id}")
    public ResponseEntity<PurchaseOrderDto> updatePurchaseOrder(@PathVariable @NonNull Long id,
                                                                 @Valid @RequestBody @NonNull PurchaseOrderDto dto) {
        return ResponseEntity.ok(purchaseOrderService.updatePurchaseOrder(id, dto));
    }

    @PreAuthorize(Authz.PURCHASE_EDIT)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePurchaseOrder(@PathVariable @NonNull Long id) {
        purchaseOrderService.deletePurchaseOrder(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize(Authz.PURCHASE_EDIT)
    @PutMapping("/{id}/receive")
    public ResponseEntity<PurchaseOrderDto> receivePurchaseOrder(
            @PathVariable @NonNull Long id,
            @RequestBody(required = false) @Valid ReceivePurchaseOrderRequest request) {
        if (request == null) {
            return ResponseEntity.ok(purchaseOrderService.receivePurchaseOrder(id));
        }
        return ResponseEntity.ok(purchaseOrderService.receivePurchaseOrder(id, request));
    }

    // -------------------------------------------------------
    // Payment endpoints
    // -------------------------------------------------------

    @PreAuthorize(Authz.PAYABLES)
    @GetMapping("/{id}/payments")
    public ResponseEntity<List<SupplierPaymentDto>> getPayments(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.getPaymentsForPurchaseOrder(id));
    }

    @PreAuthorize(Authz.PAYABLES)
    @PostMapping("/{id}/payments")
    public ResponseEntity<SupplierPaymentDto> addPayment(@PathVariable Long id,
                                                          @Valid @RequestBody AddPaymentRequest req,
                                                          Principal principal) {
        String actor = principal != null ? principal.getName() : null;
        SupplierPaymentDto dto = purchaseOrderService.addPayment(
                id, req.getAmount(), req.getPaymentMethod(), req.getNotes(), actor);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    @PreAuthorize(Authz.PAYABLES)
    @DeleteMapping("/{id}/payments/{paymentId}")
    public ResponseEntity<PurchaseOrderDto> voidPayment(@PathVariable Long id,
                                                         @PathVariable Long paymentId,
                                                         Principal principal) {
        String actor = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(purchaseOrderService.voidPayment(id, paymentId, actor));
    }
}

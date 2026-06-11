package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.PurchaseOrderDto;
import com.happydev.prestockbackend.dto.ReceivePurchaseOrderRequest;
import com.happydev.prestockbackend.dto.SupplierPaymentDto;
import com.happydev.prestockbackend.entity.PaymentMethod;
import org.springframework.lang.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderService {
    List<PurchaseOrderDto> findAllPurchaseOrders();
    Page<PurchaseOrderDto> findAllPurchaseOrders(@NonNull Pageable pageable);
    Optional<PurchaseOrderDto> findPurchaseOrderById(@NonNull Long id);
    PurchaseOrderDto createPurchaseOrder(@NonNull PurchaseOrderDto purchaseOrderDto);
    PurchaseOrderDto updatePurchaseOrder(@NonNull Long id, @NonNull PurchaseOrderDto purchaseOrderDto);
    void deletePurchaseOrder(@NonNull Long id);
    PurchaseOrderDto receivePurchaseOrder(@NonNull Long id);
    PurchaseOrderDto receivePurchaseOrder(@NonNull Long id, ReceivePurchaseOrderRequest request);

    // Payments
    SupplierPaymentDto addPayment(Long poId, BigDecimal amount, PaymentMethod paymentMethod, String notes, String actorUsername);
    List<SupplierPaymentDto> getPaymentsForPurchaseOrder(Long poId);
    PurchaseOrderDto voidPayment(Long poId, Long paymentId, String actorUsername);
    List<PurchaseOrderDto> findPayableOrders();
}
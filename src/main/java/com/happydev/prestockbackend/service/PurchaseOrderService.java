package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.PurchaseOrderDto;
import org.springframework.lang.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderService {
    List<PurchaseOrderDto> findAllPurchaseOrders();
    Page<PurchaseOrderDto> findAllPurchaseOrders(@NonNull Pageable pageable); // Paginación
    Optional<PurchaseOrderDto> findPurchaseOrderById(@NonNull Long id);
    PurchaseOrderDto createPurchaseOrder(@NonNull PurchaseOrderDto purchaseOrderDto);
    PurchaseOrderDto updatePurchaseOrder(@NonNull Long id, @NonNull PurchaseOrderDto purchaseOrderDto);
    void deletePurchaseOrder(@NonNull Long id);

    //Métodos personalizados.
    PurchaseOrderDto receivePurchaseOrder(@NonNull Long id); // Método para marcar una orden como recibida
}
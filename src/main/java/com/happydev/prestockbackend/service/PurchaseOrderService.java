package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.PurchaseOrderDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderService {
    List<PurchaseOrderDto> findAllPurchaseOrders();
    Page<PurchaseOrderDto> findAllPurchaseOrders(Pageable pageable); // Paginación
    Optional<PurchaseOrderDto> findPurchaseOrderById(Long id);
    PurchaseOrderDto createPurchaseOrder(PurchaseOrderDto purchaseOrderDto);
    PurchaseOrderDto updatePurchaseOrder(Long id, PurchaseOrderDto purchaseOrderDto);
    void deletePurchaseOrder(Long id);

    //Métodos personalizados.
    PurchaseOrderDto receivePurchaseOrder(Long id); // Método para marcar una orden como recibida
}
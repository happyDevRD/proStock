package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.StockMovement;
import com.happydev.prestockbackend.entity.StockMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StockMovementService {
    StockMovement createMovement(StockMovement movement);
    List<StockMovement> getAllMovements();
    Page<StockMovement> getAllMovements(Pageable pageable); // Paginación
    Optional<StockMovement> getMovementById(Long id);
    List<StockMovement> getMovementsByProduct(Long productId);
    List<StockMovement> getMovementsByProductAndDateRange(Long productId, LocalDateTime startDate, LocalDateTime endDate);
    List<StockMovement> getMovementsByType(StockMovementType type);
    StockMovement updateMovement(Long id, StockMovement movementDetails); // Opcional: Si permites editar movimientos
    void deleteMovement(Long id); // Opcional: Si permites eliminar movimientos
    List<StockMovement> getMovementsByPurchaseOrder(Long purchaseOrderId);
    List<StockMovement> getMovementsBySale(Long saleId);
    List<StockMovement> getMovementsBySourceLocation(Long sourceLocationId);
    List<StockMovement> getMovementsByDestinationLocation(Long destinationLocationId);
    List<StockMovement> getMovementsByUser(Long userId); //Si tienes usuarios.
    List<StockMovement> getMovementsByBatchNumber(String batchNumber);
    int calculateCurrentStock(Long productId); // Método para calcular el stock actual
    Optional<StockMovement> findLatestStockMovement(Long productId);

}

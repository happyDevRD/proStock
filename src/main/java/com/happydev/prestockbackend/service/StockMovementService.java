package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.StockMovement;
import com.happydev.prestockbackend.entity.StockMovementType;
import org.springframework.lang.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StockMovementService {
    StockMovement createMovement(@NonNull StockMovement movement);
    List<StockMovement> getAllMovements();
    Page<StockMovement> getAllMovements(@NonNull Pageable pageable); // Paginación
    Optional<StockMovement> getMovementById(@NonNull Long id);
    List<StockMovement> getMovementsByProduct(@NonNull Long productId);
    List<StockMovement> getMovementsByProductAndDateRange(@NonNull Long productId, @NonNull LocalDateTime startDate, @NonNull LocalDateTime endDate);
    List<StockMovement> getMovementsByType(@NonNull StockMovementType type);
    StockMovement updateMovement(@NonNull Long id, @NonNull StockMovement movementDetails); // Opcional: Si permites editar movimientos
    void deleteMovement(@NonNull Long id); // Opcional: Si permites eliminar movimientos
    List<StockMovement> getMovementsByPurchaseOrder(@NonNull Long purchaseOrderId);
    List<StockMovement> getMovementsBySale(@NonNull Long saleId);
    List<StockMovement> getMovementsBySourceLocation(@NonNull Long sourceLocationId);
    List<StockMovement> getMovementsByDestinationLocation(@NonNull Long destinationLocationId);
    List<StockMovement> getMovementsByUser(@NonNull Long userId); //Si tienes usuarios.
    List<StockMovement> getMovementsByBatchNumber(@NonNull String batchNumber);
    int calculateCurrentStock(@NonNull Long productId); // Método para calcular el stock actual
    Optional<StockMovement> findLatestStockMovement(@NonNull Long productId);

}

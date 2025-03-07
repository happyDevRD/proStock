package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.StockMovement;
import com.happydev.prestockbackend.entity.StockMovementType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.time.LocalDateTime;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    // Obtener todos los movimientos de stock de un producto
    List<StockMovement> findByProduct_IdOrderByMovementDateDesc(Long productId);

    // Obtener movimientos de stock de un producto en un rango de fechas
    List<StockMovement> findByProduct_IdAndMovementDateBetweenOrderByMovementDateDesc(
            Long productId, LocalDateTime startDate, LocalDateTime endDate);

    // Obtener movimientos de stock por tipo
    List<StockMovement> findByTypeOrderByMovementDateDesc(StockMovementType type);

    // Obtener movimientos de un tipo específico para un producto
    List<StockMovement> findByProduct_IdAndTypeOrderByMovementDateDesc(Long productId, StockMovementType type);

    // Obtener movimientos relacionados con una orden de compra
    List<StockMovement> findByPurchaseOrder_IdOrderByMovementDateDesc(Long purchaseOrderId);

    // Obtener movimientos relacionados con una venta
    List<StockMovement> findBySale_IdOrderByMovementDateDesc(Long saleId);

    // Obtener movimientos por ubicación de origen
    List<StockMovement> findBySourceLocationIdOrderByMovementDateDesc(Long sourceLocationId);

    // Obtener movimientos por ubicación de destino
    List<StockMovement> findByDestinationLocationIdOrderByMovementDateDesc(Long destinationLocationId);

    // Obtener movimientos por usuario
    List<StockMovement> findByUser_IdOrderByMovementDateDesc(Long userId); // Si tienes usuarios

    // Obtener movimientos por lote
    List<StockMovement> findByBatchNumberOrderByMovementDateDesc(String batchNumber);

    // Obtener los movimientos de stock de un producto ordenados por fecha de movimiento de forma descendente.
    List<StockMovement> findByProductIdOrderByMovementDateDesc(Long productId);

    // Obtener los movimientos de stock de un producto dentro de un rango de fechas.
    List<StockMovement> findByProductIdAndMovementDateBetween(
            Long productId, LocalDateTime startDateTime, LocalDateTime endDateTime);

    //  Método para calcular el stock actual de un producto
    @Query("SELECT COALESCE(SUM(sm.quantityChange), 0) FROM StockMovement sm WHERE sm.product.id = :productId")
    int calculateCurrentStock(@Param("productId") Long productId);

    // Obtener el último movimiento de stock de un producto
    @Query("SELECT sm FROM StockMovement sm WHERE sm.product.id = :productId ORDER BY sm.movementDate DESC")
    List<StockMovement> findLatestStockMovement(@Param("productId") Long productId, Pageable pageable);
}
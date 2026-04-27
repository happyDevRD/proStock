package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.Product;
import com.happydev.prestockbackend.entity.StockMovement;
import com.happydev.prestockbackend.entity.StockMovementType;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.StockMovementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;

@Service
public class StockMovementServiceImpl implements StockMovementService {

    private final StockMovementRepository stockMovementRepository;

    private final ProductRepository productRepository; // Necesario para obtener el stock actual

    public StockMovementServiceImpl(StockMovementRepository stockMovementRepository, ProductRepository productRepository) {
        this.stockMovementRepository = stockMovementRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public StockMovement createMovement(StockMovement movement) {
        Product product = productRepository.findById(movement.getProduct().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", movement.getProduct().getId()));
        int currentStock = product.getStock() != null ? product.getStock() : 0;
        int stockAfter = currentStock + movement.getQuantityChange();

        if (movement.getType() != StockMovementType.TRANSFER && stockAfter < 0) {
            throw new IllegalStateException("Stock cannot be negative for product id: " + product.getId());
        }

        movement.setProduct(product);
        movement.setStockBefore(currentStock);
        movement.setStockAfter(stockAfter);

        // Actualizar el stock del producto (si no es una transferencia)
        if (movement.getType() != StockMovementType.TRANSFER) {
            product.setStock(stockAfter);
            productRepository.save(product);
        }

        return stockMovementRepository.save(movement);
    }


    @Override
    @Transactional(readOnly = true)
    public List<StockMovement> getAllMovements() {
        return stockMovementRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockMovement> getAllMovements(Pageable pageable) {
        return stockMovementRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockMovement> getMovementById(Long id) {
        return stockMovementRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovement> getMovementsByProduct(Long productId) {
        return stockMovementRepository.findByProduct_IdOrderByMovementDateDesc(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovement> getMovementsByProductAndDateRange(Long productId, LocalDateTime startDate, LocalDateTime endDate) {
        return stockMovementRepository.findByProduct_IdAndMovementDateBetweenOrderByMovementDateDesc(productId, startDate, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovement> getMovementsByType(StockMovementType type) {
        return stockMovementRepository.findByTypeOrderByMovementDateDesc(type);
    }

    @Override
    @Transactional
    public StockMovement updateMovement(Long id, StockMovement movementDetails) {
        StockMovement movement = stockMovementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockMovement", "id", id));

        // Actualiza los campos (excepto los que no deben cambiar, como product_id, movementDate, etc.)
        movement.setQuantityChange(movementDetails.getQuantityChange());
        movement.setType(movementDetails.getType());
        movement.setReason(movementDetails.getReason());
        movement.setBatchNumber(movementDetails.getBatchNumber());
        movement.setExpirationDate(movementDetails.getExpirationDate());
        movement.setUnitCost(movementDetails.getUnitCost());

        // Recalcular stockBefore y stockAfter
        int currentStock = calculateCurrentStock(movement.getProduct().getId());
        movement.setStockBefore(currentStock - movement.getQuantityChange()); // Ajuste para recalcular
        movement.setStockAfter(currentStock);


        // Actualizar el stock del producto
        Product product = productRepository.findById(movement.getProduct().getId()).orElseThrow(()-> new ResourceNotFoundException("Product", "id", id));
        product.setStock(movement.getStockAfter()); // Se actualiza en base al stockAfter YA calculado.
        productRepository.save(product);

        return stockMovementRepository.save(movement);
    }

    @Override
    @Transactional
    public void deleteMovement(Long id) {
        StockMovement movement = stockMovementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockMovement", "id", id));

        // Restaurar el stock del producto (si no es una transferencia)
        if (movement.getType() != StockMovementType.TRANSFER) {
            Product product = movement.getProduct();
            product.setStock(product.getStock() - movement.getQuantityChange());
            productRepository.save(product);
        }

        stockMovementRepository.delete(movement);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovement> getMovementsByPurchaseOrder(Long purchaseOrderId) {
        return stockMovementRepository.findByPurchaseOrder_IdOrderByMovementDateDesc(purchaseOrderId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovement> getMovementsBySale(Long saleId) {
        return stockMovementRepository.findBySale_IdOrderByMovementDateDesc(saleId);
    }
    @Override
    @Transactional(readOnly = true)
    public List<StockMovement> getMovementsBySourceLocation(Long sourceLocationId) {
        return stockMovementRepository.findBySourceLocationIdOrderByMovementDateDesc(sourceLocationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovement> getMovementsByDestinationLocation(Long destinationLocationId) {
        return stockMovementRepository.findByDestinationLocationIdOrderByMovementDateDesc(destinationLocationId);
    }
    @Override
    @Transactional(readOnly = true)
    public List<StockMovement> getMovementsByUser(Long userId) {
        return stockMovementRepository.findByUser_IdOrderByMovementDateDesc(userId);
    }

    @Override
    public List<StockMovement> getMovementsByBatchNumber(String batchNumber) {
        return stockMovementRepository.findByBatchNumberOrderByMovementDateDesc(batchNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public int calculateCurrentStock(Long productId) {
        return stockMovementRepository.calculateCurrentStock(productId);
    }

    @Override
    public Optional<StockMovement> findLatestStockMovement(Long productId) {
        return stockMovementRepository.findLatestStockMovement(productId, PageRequest.of(0, 1)).stream().findFirst();
    }
}
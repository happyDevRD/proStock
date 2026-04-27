package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.ProductDto;
import com.happydev.prestockbackend.entity.StockMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductService {

    Page<ProductDto> findAllProducts(@NonNull Pageable pageable); // CON Pageable

    List<ProductDto> findAllProducts();       //También el que no recibe parámetro.

    Optional<ProductDto> findProductById(@NonNull Long id);

    ProductDto saveProduct(@NonNull ProductDto ProductDto);

    ProductDto updateProduct(@NonNull Long id, @NonNull ProductDto productDetails);

    void deleteProduct(@NonNull Long id);

    // Nuevo método para alertas de stock bajo
    List<ProductDto> findProductsBelowMinStock();
    Page<ProductDto> findProductsBelowMinStock(@NonNull Pageable pageable);

    void adjustStock(@NonNull Long productId,
                     int quantityChange,
                     @NonNull StockMovementType type,
                     String reason,
                     String batchNumber,
                     LocalDateTime expirationDate,
                     BigDecimal unitCost,
                     Long sourceLocationId,
                     Long destinationLocationId);

}

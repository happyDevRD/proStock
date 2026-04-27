package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.ProductDto;
import com.happydev.prestockbackend.entity.StockMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductService {

    Page<ProductDto> findAllProducts(Pageable pageable); // CON Pageable

    List<ProductDto> findAllProducts();       //También el que no recibe parámetro.

    Optional<ProductDto> findProductById(Long id);

    ProductDto saveProduct(ProductDto ProductDto);

    ProductDto updateProduct(Long id, ProductDto productDetails);

    void deleteProduct(Long id);

    // Nuevo método para alertas de stock bajo
    List<ProductDto> findProductsBelowMinStock();
    Page<ProductDto> findProductsBelowMinStock(Pageable pageable);

    void adjustStock(Long productId,
                     int quantityChange,
                     StockMovementType type,
                     String reason,
                     String batchNumber,
                     LocalDateTime expirationDate,
                     BigDecimal unitCost,
                     Long sourceLocationId,
                     Long destinationLocationId);

}

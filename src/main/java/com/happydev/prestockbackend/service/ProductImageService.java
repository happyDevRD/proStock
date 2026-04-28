package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.ProductImage;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface ProductImageService {
    List<ProductImage> findAllProductImages();
    Optional<ProductImage> findProductImageById(@NonNull Long id);
    ProductImage saveProductImage(@NonNull ProductImage productImage);
    ProductImage updateProductImage(@NonNull Long id); // SOLO EL ID
    void deleteProductImage(@NonNull Long id);
    List<ProductImage> findByProductId(@NonNull Long productId);
}


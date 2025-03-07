package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.ProductImage;

import java.util.List;
import java.util.Optional;

public interface ProductImageService {
    List<ProductImage> findAllProductImages();
    Optional<ProductImage> findProductImageById(Long id);
    ProductImage saveProductImage(ProductImage productImage);
    ProductImage updateProductImage(Long id); // SOLO EL ID
    void deleteProductImage(Long id);
    List<ProductImage> findByProductId(Long productId);
}


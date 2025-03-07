package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    // Método personalizado para buscar imágenes por ID de producto
    List<ProductImage> findByProductId(Long productId);
}

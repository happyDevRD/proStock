package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.ProductImage;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.ProductImageRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.util.SecurityAuditUtils;
import jakarta.transaction.Transactional;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;
    private final AuditService auditService;

    public ProductImageServiceImpl(
            ProductImageRepository productImageRepository,
            ProductRepository productRepository,
            AuditService auditService
    ) {
        this.productImageRepository = productImageRepository;
        this.productRepository = productRepository;
        this.auditService = auditService;
    }


    @Override
    public List<ProductImage> findAllProductImages() {
        return productImageRepository.findAll();
    }


    @Override
    public Optional<ProductImage> findProductImageById(@NonNull Long id) {
        return productImageRepository.findById(id);
    }

    @Override
    public ProductImage saveProductImage(@NonNull ProductImage productImage) {
        // Asegúrate de que productImage.getProduct() NO sea null antes de guardar.
        if (productImage.getProduct() == null) {
            throw new IllegalArgumentException("Product cannot be null for ProductImage"); // O una excepción más específica
        }

        //Verificar si existe el producto.
        Long validatedProductId = productImage.getProduct().getId();
        if (validatedProductId == null || !productRepository.existsById(validatedProductId)) {
            throw new ResourceNotFoundException("Product", "id", validatedProductId);

        }

        ProductImage saved = productImageRepository.save(productImage);
        Long productId = saved.getProduct() != null ? saved.getProduct().getId() : null;
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "PRODUCT_IMAGE_CREATED",
                "ProductImage",
                saved.getId(),
                Map.of("productId", productId != null ? productId.toString() : "")
        );
        return saved;
    }


    @Override
    public ProductImage updateProductImage(@NonNull Long id) { // SOLO EL ID
        ProductImage productImage = productImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductImage", "id", id));

        // Ya no necesitas validaciones, el controlador maneja la actualizacion

        ProductImage saved = productImageRepository.save(Objects.requireNonNull(productImage)); // Guarda los cambios
        Long productId = saved.getProduct() != null ? saved.getProduct().getId() : null;
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "PRODUCT_IMAGE_UPDATED",
                "ProductImage",
                id,
                Map.of("productId", productId != null ? productId.toString() : "")
        );
        return saved;
    }

    @Override
    public void deleteProductImage(@NonNull Long id) {
        ProductImage productImage = productImageRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("ProductImage", "id", id));
        Long productId = productImage.getProduct() != null ? productImage.getProduct().getId() : null;
        productImageRepository.delete(Objects.requireNonNull(productImage));
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "PRODUCT_IMAGE_DELETED",
                "ProductImage",
                id,
                Map.of("productId", productId != null ? productId.toString() : "")
        );
    }

    @Override
    public List<ProductImage> findByProductId(@NonNull Long productId) {
        return productImageRepository.findByProductId(productId);
    }
}


package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.ProductImage;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.ProductImageRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;

    public ProductImageServiceImpl(ProductImageRepository productImageRepository, ProductRepository productRepository) {
        this.productImageRepository = productImageRepository;
        this.productRepository = productRepository;
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
        Long productId = productImage.getProduct().getId();
        if (productId == null || !productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product", "id", productId);

        }

        return productImageRepository.save(productImage);
    }


    @Override
    public ProductImage updateProductImage(@NonNull Long id) { // SOLO EL ID
        ProductImage productImage = productImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductImage", "id", id));

        // Ya no necesitas validaciones, el controlador maneja la actualizacion

        return productImageRepository.save(Objects.requireNonNull(productImage)); // Guarda los cambios
    }

    @Override
    public void deleteProductImage(@NonNull Long id) {
        ProductImage productImage = productImageRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("ProductImage", "id", id));
        productImageRepository.delete(Objects.requireNonNull(productImage));
    }

    @Override
    public List<ProductImage> findByProductId(@NonNull Long productId) {
        return productImageRepository.findByProductId(productId);
    }
}


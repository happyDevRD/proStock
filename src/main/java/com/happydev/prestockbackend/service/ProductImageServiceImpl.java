package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.ProductImage;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.ProductImageRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
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
    public Optional<ProductImage> findProductImageById(Long id) {
        return productImageRepository.findById(id);
    }

    @Override
    public ProductImage saveProductImage(ProductImage productImage) {
        // Asegúrate de que productImage.getProduct() NO sea null antes de guardar.
        if (productImage.getProduct() == null) {
            throw new IllegalArgumentException("Product cannot be null for ProductImage"); // O una excepción más específica
        }

        //Verificar si existe el producto.
        if (productImage.getProduct().getId() == null ||
                !productRepository.existsById(productImage.getProduct().getId()) ) {
            throw new ResourceNotFoundException("Product", "id", productImage.getProduct().getId());

        }

        return productImageRepository.save(productImage);
    }


    @Override
    public ProductImage updateProductImage(Long id) { // SOLO EL ID
        ProductImage productImage = productImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductImage", "id", id));

        // Ya no necesitas validaciones, el controlador maneja la actualizacion

        return productImageRepository.save(productImage); // Guarda los cambios
    }

    @Override
    public void deleteProductImage(Long id) {
        ProductImage productImage = productImageRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("ProductImage", "id", id));
        productImageRepository.delete(productImage);
    }

    @Override
    public List<ProductImage> findByProductId(Long productId) {
        return productImageRepository.findByProductId(productId);
    }
}


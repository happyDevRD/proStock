package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.ProductDto;
import com.happydev.prestockbackend.dto.ProductImageDto;
import com.happydev.prestockbackend.entity.Category;
import com.happydev.prestockbackend.entity.IndicadorFacturacion;
import com.happydev.prestockbackend.entity.Product;
import com.happydev.prestockbackend.entity.ProductImage;
import com.happydev.prestockbackend.entity.ProductStatus;
import com.happydev.prestockbackend.entity.Supplier;
import com.happydev.prestockbackend.entity.TipoBienServicio;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.mapper.ProductMapper;
import com.happydev.prestockbackend.repository.CategoryRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private ProductMapper productMapper;
    @InjectMocks
    private ProductServiceImpl productService;

    private Product product;
    private ProductDto productDto;
    private Category category;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setId(1L);
        category.setName("Electrónicos");

        supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName("Proveedor S.A.");

        product = new Product();
        product.setId(1L);
        product.setSku("SKU-123");
        product.setName("Producto de Prueba");
        product.setDescription("Descripción del producto");
        product.setCostPrice(BigDecimal.valueOf(10.0));
        product.setSellingPrice(BigDecimal.valueOf(20.0));
        product.setStock(50);
        product.setMinStock(5);
        product.setCategory(category);
        product.setSupplier(supplier);
        product.setIndicadorFacturacion(IndicadorFacturacion.ITBIS_18);
        product.setTipoBienServicio(TipoBienServicio.BIEN);
        product.setUnidadMedida(43);
        product.setStatus(ProductStatus.ACTIVE); // Nuevo campo
        product.setBarcode("123456789012"); // Nuevo campo
        product.setTaxRate(BigDecimal.ZERO); // Configura un valor para taxRate
        product.setImages(new java.util.ArrayList<>());

        productDto = new ProductDto();
        productDto.setId(1L);
        productDto.setSku("SKU-123");
        productDto.setName("Producto de Prueba");
        productDto.setDescription("Descripción del producto");
        productDto.setCostPrice(BigDecimal.valueOf(10.0));
        productDto.setSellingPrice(BigDecimal.valueOf(20.0));
        productDto.setStock(50);
        productDto.setMinStock(5);
        productDto.setCategoryId(1L);  // Usar ID de categoría
        productDto.setSupplierId(1L);  // Usar ID de proveedor
        productDto.setIndicadorFacturacion(IndicadorFacturacion.ITBIS_18);
        productDto.setTipoBienServicio(TipoBienServicio.BIEN);
        productDto.setUnidadMedida(43);
        productDto.setStatus(ProductStatus.ACTIVE); // Nuevo campo
        productDto.setBarcode("123456789012"); // Nuevo campo
        productDto.setTaxRate(BigDecimal.ZERO);
        ProductImageDto imageDto = new ProductImageDto();
        imageDto.setUrl("producto.png");
        productDto.setImages(Collections.singletonList(imageDto));

    }

    @Test
    void findAllProducts_ReturnsListOfProducts() {
        when(productRepository.findAll()).thenReturn(Arrays.asList(product));
        when(productMapper.toDtoList(anyList())).thenReturn(Arrays.asList(productDto));

        List<ProductDto> products = productService.findAllProducts();

        assertFalse(products.isEmpty());
        assertEquals(1, products.size());
        verify(productRepository).findAll();
    }

    @Test
    void findAllProducts_WithPagination_ReturnsPageOfProducts() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> productPage = new PageImpl<>(Arrays.asList(product), pageable, 1);
        when(productRepository.findAll(pageable)).thenReturn(productPage);
        when(productMapper.toDto(product)).thenReturn(productDto);

        Page<ProductDto> resultPage = productService.findAllProducts(pageable);

        assertFalse(resultPage.isEmpty());
        assertEquals(1, resultPage.getTotalElements());
        verify(productRepository).findAll(pageable);
    }
    @Test
    void findProductById_ExistingProduct_ReturnsProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productMapper.toDto(product)).thenReturn(productDto);

        Optional<ProductDto> foundProduct = productService.findProductById(1L);

        assertTrue(foundProduct.isPresent());
        verify(productRepository).findById(1L);
    }

    @Test
    void findProductById_NonExistingProduct_ReturnsEmptyOptional() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());
        Optional<ProductDto> foundProduct = productService.findProductById(1L);
        assertTrue(foundProduct.isEmpty());
        verify(productRepository).findById(1L);
    }


    @Test
    void createProduct_ValidProduct_ReturnsCreatedProduct() {
        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(supplierRepository.existsById(1L)).thenReturn(true);
        when(productRepository.existsBySkuIgnoreCase(anyString())).thenReturn(false);
        when(productRepository.existsByBarcodeIgnoreCase(anyString())).thenReturn(false);
        when(productMapper.toEntity(productDto)).thenReturn(product);
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(productMapper.toDto(any(Product.class))).thenReturn(productDto);


        ProductDto createdProduct = productService.saveProduct(productDto);

        assertNotNull(createdProduct);
        verify(productRepository).save(any(Product.class));
    }


    @Test
    void updateProduct_ExistingProduct_ReturnsUpdatedProduct() {

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.existsBySkuIgnoreCaseAndIdNot(anyString(), anyLong())).thenReturn(false);
        when(productRepository.existsByBarcodeIgnoreCaseAndIdNot(anyString(), anyLong())).thenReturn(false);
        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(supplierRepository.existsById(1L)).thenReturn(true);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(productMapper.toImageEntityList(anyList())).thenReturn(Collections.<ProductImage>emptyList());
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(productMapper.toDto(product)).thenReturn(productDto); //Simulamos DTO
        doNothing().when(productMapper).updateProductFromDto(any(ProductDto.class), any(Product.class)); // Mock update method

        ProductDto updatedProduct = productService.updateProduct(1L, productDto);

        assertNotNull(updatedProduct);
        verify(productRepository).findById(1L);
        verify(productRepository).save(any(Product.class));
    }


    @Test
    void updateProduct_NonExistingProduct_ThrowsException() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.updateProduct(1L, productDto));
        verify(productRepository).findById(1L);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void deleteProduct_ExistingProduct_DeletesProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        productService.deleteProduct(1L);
        verify(productRepository).findById(1L);
        verify(productRepository).deleteById(1L);
    }

    @Test
    void deleteProduct_NonExistingProduct_ThrowsException() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> productService.deleteProduct(1L));
        verify(productRepository).findById(1L);
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void createProduct_withNonExistingCategory_ThrowsResourceNotFoundException() {
        productDto.setCategoryId(99L); // ID de categoría inexistente

        when(categoryRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> productService.saveProduct(productDto));
    }

    @Test
    void createProduct_withNonExistingSupplier_ThrowsResourceNotFoundException() {
        productDto.setSupplierId(99L);
        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(supplierRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> productService.saveProduct(productDto));
    }
}
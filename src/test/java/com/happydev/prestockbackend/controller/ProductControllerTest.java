package com.happydev.prestockbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.dto.ProductDto;
import com.happydev.prestockbackend.entity.Category;
import com.happydev.prestockbackend.entity.Supplier;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;  // BDDMockito
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class) // Prueba solo el controlador
@SuppressWarnings("null")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc; // Para simular peticiones HTTP

    @MockitoBean // Mock del servicio (no interactuamos con la DB real)
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper; // Para convertir objetos a JSON y viceversa.

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

        productDto = new ProductDto();
        productDto.setId(1L);
        productDto.setSku("SKU-123");
        productDto.setName("Producto de Prueba");
        productDto.setDescription("Descripción del producto");
        productDto.setCostPrice(BigDecimal.valueOf(10.0));
        productDto.setSellingPrice(BigDecimal.valueOf(20.0));
        productDto.setStock(50);
        productDto.setMinStock(5);
        productDto.setCategoryId(1L); // Usar IDs
        productDto.setSupplierId(1L); // Usar IDs
    }

    @Test
    void getAllProducts_ReturnsListOfProducts() throws Exception {
        // Configura el mock del servicio para devolver una Page<ProductDto>
        Page<ProductDto> productDtoPage = new PageImpl<>(Arrays.asList(productDto));
        given(productService.findAllProducts(any(Pageable.class))).willReturn(productDtoPage);

        ResultActions response = mockMvc.perform(get("/api/products")
                .contentType(MediaType.APPLICATION_JSON));

        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content.size()", is(1))) // Verifica el tamaño de la lista *dentro* de "content"
                .andExpect(jsonPath("$.content[0].name", is(productDto.getName()))); // Accede al primer elemento de "content"
    }

    @Test
    void getProductById_ExistingProduct_ReturnsProduct() throws Exception {
        given(productService.findProductById(1L)).willReturn(Optional.of(productDto));

        ResultActions response = mockMvc.perform(get("/api/products/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON));

        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(productDto.getName())));
    }

    @Test
    void getProductById_NonExistingProduct_ReturnsNotFound() throws Exception {
        given(productService.findProductById(1L)).willReturn(Optional.empty());

        ResultActions response = mockMvc.perform(get("/api/products/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON));

        response.andExpect(status().isNotFound()); // 404 Not Found
    }

    @Test
    void createProduct_ValidProduct_ReturnsCreatedProduct() throws Exception {
        given(productService.saveProduct(any(ProductDto.class))).willReturn(productDto);

        ResultActions response = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(productDto))); // Convierte el DTO a JSON

        response.andExpect(status().isCreated()) // 201 Created
                .andExpect(jsonPath("$.name", is(productDto.getName())));
    }

    @Test
    void updateProduct_ExistingProduct_ReturnsUpdatedProduct() throws Exception {
        given(productService.updateProduct(eq(1L), any(ProductDto.class))).willReturn(productDto);

        ResultActions response = mockMvc.perform(put("/api/products/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(productDto)));

        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(productDto.getName())));
    }

    @Test
    void updateProduct_NonExistingProduct_ReturnsNotFound() throws Exception {
        given(productService.updateProduct(eq(1L), any(ProductDto.class))).willThrow(ResourceNotFoundException.class);

        ResultActions response = mockMvc.perform(put("/api/products/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(productDto)));

        response.andExpect(status().isNotFound()); // 404 Not Found
    }

    @Test
    void deleteProduct_ExistingProduct_ReturnsNoContent() throws Exception {
        doNothing().when(productService).deleteProduct(1L); // No retorna nada (void)

        ResultActions response = mockMvc.perform(delete("/api/products/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON));

        response.andExpect(status().isNoContent()); // 204 No Content
    }

    @Test
    void deleteProduct_NonExistingProduct_ReturnsNotFound() throws Exception {
        doThrow(ResourceNotFoundException.class).when(productService).deleteProduct(1L);

        ResultActions response = mockMvc.perform(delete("/api/products/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON));

        response.andExpect(status().isNotFound());
    }

    @Test
    void createProduct_InvalidProduct_ReturnsBadRequest() throws Exception {
        // Crea un DTO inválido (por ejemplo, sin nombre)
        ProductDto invalidProductDto = new ProductDto();
        invalidProductDto.setSku("SKU-123"); // Solo SKU, sin nombre
        // ... otros campos, pero sin lo requerido ...

        ResultActions response = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidProductDto)));

        response.andExpect(status().isBadRequest()); // 400 Bad Request
    }
}

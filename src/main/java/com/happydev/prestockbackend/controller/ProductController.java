package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.ProductDto;
import com.happydev.prestockbackend.dto.StockAdjustmentRequestDto;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/products") // Ruta base para todos los endpoints de productos
@Tag(name = "Products", description = "Endpoints for managing products") // Agrupa los endpoints
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Get all products", description = "Retrieves a list of all products.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list of products",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProductDto.class))}), //Define el Schema
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    public ResponseEntity<Page<ProductDto>> getAllProducts(Pageable pageable) { //Recibe Pageable
        Page<ProductDto> products = productService.findAllProducts(pageable);
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    @Operation(summary = "Get a product by ID", description = "Retrieves a product by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved product",
                    content = { @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProductDto.class)) }),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getProductById(@PathVariable Long id) {
        return productService.findProductById(id)
                .map(productDto -> new ResponseEntity<>(productDto, HttpStatus.OK)) //Ya se retorna un DTO
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }


    @Operation(summary = "Create a new product", description = "Creates a new product.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Product created successfully",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProductDto.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody ProductDto productDto) {
        ProductDto savedProduct = productService.saveProduct(productDto); //Guarda y retorna DTO
        return new ResponseEntity<>(savedProduct, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable Long id, @RequestBody ProductDto productDetails) {
        try {
            ProductDto updatedProduct = productService.updateProduct(id, productDetails);
            return new ResponseEntity<>(updatedProduct, HttpStatus.OK);
        } catch (ResourceNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        try{
            productService.deleteProduct(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT); // 204 No Content
        } catch (ResourceNotFoundException e){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }


    @GetMapping("/below-min-stock")
    public ResponseEntity<List<ProductDto>> getProductsBelowMinStock() {
        List<ProductDto> products = productService.findProductsBelowMinStock();
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    @GetMapping("/below-min-stock/paginated")
    public ResponseEntity<Page<ProductDto>> getProductsBelowMinStock(Pageable pageable) {
        Page<ProductDto> products = productService.findProductsBelowMinStock(pageable);
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    @PostMapping("/{id}/stock-adjustments")
    public ResponseEntity<Void> adjustStock(@PathVariable Long id, @Valid @RequestBody StockAdjustmentRequestDto request) {
        productService.adjustStock(
                id,
                request.getQuantityChange(),
                request.getType(),
                request.getReason(),
                request.getBatchNumber(),
                request.getExpirationDate(),
                request.getUnitCost(),
                request.getSourceLocationId(),
                request.getDestinationLocationId()
        );
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
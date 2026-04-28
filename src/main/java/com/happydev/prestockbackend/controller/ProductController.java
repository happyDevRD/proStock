package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.ProductDto;
import com.happydev.prestockbackend.dto.StockAdjustmentRequestDto;
import com.happydev.prestockbackend.entity.StockMovementType;
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
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Objects;

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
    public ResponseEntity<Page<ProductDto>> getAllProducts(@NonNull Pageable pageable) { //Recibe Pageable
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
    public ResponseEntity<ProductDto> getProductById(@PathVariable @NonNull Long id) {
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
    public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody @NonNull ProductDto productDto) {
        ProductDto savedProduct = productService.saveProduct(productDto); //Guarda y retorna DTO
        return new ResponseEntity<>(savedProduct, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable @NonNull Long id, @RequestBody @NonNull ProductDto productDetails) {
        ProductDto updatedProduct = productService.updateProduct(id, productDetails);
        return new ResponseEntity<>(updatedProduct, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable @NonNull Long id) {
        productService.deleteProduct(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT); // 204 No Content
    }


    @GetMapping("/below-min-stock")
    public ResponseEntity<List<ProductDto>> getProductsBelowMinStock() {
        List<ProductDto> products = productService.findProductsBelowMinStock();
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    @GetMapping("/below-min-stock/paginated")
    public ResponseEntity<Page<ProductDto>> getProductsBelowMinStock(@NonNull Pageable pageable) {
        Page<ProductDto> products = productService.findProductsBelowMinStock(pageable);
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ProductDto>> searchProducts(@RequestParam("q") @NonNull String query, @NonNull Pageable pageable) {
        Page<ProductDto> products = productService.searchProducts(query, pageable);
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    @PostMapping("/import-csv")
    public ResponseEntity<List<ProductDto>> importProductsFromCsv(@RequestParam("file") @NonNull MultipartFile file) {
        List<ProductDto> importedProducts = productService.importProductsFromCsv(file);
        return new ResponseEntity<>(importedProducts, HttpStatus.CREATED);
    }

    @PostMapping("/{id}/stock-adjustments")
    public ResponseEntity<Void> adjustStock(@PathVariable @NonNull Long id, @Valid @RequestBody @NonNull StockAdjustmentRequestDto request) {
        StockMovementType type = Objects.requireNonNull(request.getType(), "Stock movement type is required");
        String reason = Objects.requireNonNull(request.getReason(), "Stock movement reason is required");
        productService.adjustStock(
                id,
                request.getQuantityChange(),
                type,
                reason,
                request.getBatchNumber(),
                request.getExpirationDate(),
                request.getUnitCost(),
                request.getSourceLocationId(),
                request.getDestinationLocationId()
        );
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
package com.happydev.prestockbackend.controller;


import com.happydev.prestockbackend.dto.ProductImageDto;
import com.happydev.prestockbackend.entity.Product;
import com.happydev.prestockbackend.entity.ProductImage;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.mapper.ProductImageMapper;
import com.happydev.prestockbackend.repository.ProductImageRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.service.FileStorageService;
import com.happydev.prestockbackend.service.ProductImageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.List;

@RestController
@RequestMapping("/api/product-images")
public class ProductImageController {

    private final ProductImageService productImageService;
    private final FileStorageService fileStorageService;
    private final ProductRepository productRepository;
    private final ProductImageMapper productImageMapper; //Para convertir a DTO
    private final ProductImageRepository productImageRepository; // Inyecta el repositorio


    public ProductImageController(ProductImageService productImageService,
                                  FileStorageService fileStorageService,
                                  ProductRepository productRepository,
                                  ProductImageMapper productImageMapper,
                                  ProductImageRepository productImageRepository) { //Agrega al constructor
        this.productImageService = productImageService;
        this.fileStorageService = fileStorageService;
        this.productRepository = productRepository;
        this.productImageMapper = productImageMapper;
        this.productImageRepository = productImageRepository; // Inyecta
    }

    @GetMapping
    public ResponseEntity<List<ProductImage>> getAllProductImages() {
        List<ProductImage> images = productImageService.findAllProductImages();
        return new ResponseEntity<>(images, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductImage> getProductImageById(@PathVariable @NonNull Long id) {
        return productImageService.findProductImageById(id)
                .map(image -> new ResponseEntity<>(image, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    //Obtener imágenes por id de producto
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<ProductImage>> getProductImagesByProductId(@PathVariable @NonNull Long productId) {
        List<ProductImage> images = productImageService.findByProductId(productId);
        return new ResponseEntity<>(images, HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ProductImageDto> uploadImage(@RequestParam("file") @NonNull MultipartFile file,
                                                       @RequestParam("productId") @NonNull Long productId) { //Recibe el archivo y el productId

        try {
            // Guardar el archivo
            String filename = fileStorageService.store(file);

            // Obtener el producto
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

            // Crear la entidad ProductImage
            ProductImage productImage = new ProductImage();
            productImage.setFileName(filename);
            productImage.setProduct(product);  // Establecer la relación con el producto
            ProductImage savedImage = productImageRepository.save(productImage);

            return new ResponseEntity<>(productImageMapper.toDto(savedImage), HttpStatus.CREATED);

        } catch (IOException e) {
            // Manejar la excepción (por ejemplo, devolver un error 500)
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/files/{filename:.+}") //Permite extensiones
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        try {
            Resource file = fileStorageService.loadAsResource(filename);
            // Obtener el tipo MIME del archivo
            String contentType = Files.probeContentType(file.getFile().toPath());

            if(contentType == null) {
                contentType = "application/octet-stream"; // Tipo MIME por defecto
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType) // Establece el tipo MIME correcto
                    .body(file);
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND); // O un error más específico
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteImage(@PathVariable @NonNull Long id) {
        ProductImage image = productImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductImage", "id", id));
        try {
            fileStorageService.delete(Objects.requireNonNull(image.getFileName())); // Elimina el archivo
            productImageRepository.delete(image);          // Elimina la entidad
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);

        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductImageDto> updateImage(@PathVariable @NonNull Long id,
                                                       @RequestParam("file") @NonNull MultipartFile file) { //Solo el archivo

        ProductImage productImage = productImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductImage", "id", id));
        try {
            // Eliminar el archivo anterior
            fileStorageService.delete(Objects.requireNonNull(productImage.getFileName()));

            // Guardar el nuevo archivo
            String filename = fileStorageService.store(file);

            //Actualizamos
            productImage.setFileName(filename);
            ProductImage updatedImage = productImageRepository.save(productImage); //Actualiza
            return new ResponseEntity<>(productImageMapper.toDto(updatedImage), HttpStatus.OK);

        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR); // O un error más específico

        }
    }
}

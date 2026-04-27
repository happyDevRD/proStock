package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.ProductDto;
import com.happydev.prestockbackend.dto.ProductImageDto;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.mapper.ProductMapper;
import com.happydev.prestockbackend.repository.CategoryRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.SupplierRepository;
import com.happydev.prestockbackend.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository; // Inyecta CategoryRepository
    private final SupplierRepository supplierRepository; // Inyecta SupplierRepository
    private final ProductMapper productMapper; //Inyectar el mapper
    private final StockMovementService stockMovementService; //Inyectar el mapper
    private final UserRepository userRepository; //Inyectar el mapper

    private static final Logger logger = LoggerFactory.getLogger(ProductServiceImpl.class); //Logger


    public ProductServiceImpl(ProductRepository productRepository,
                              CategoryRepository categoryRepository,
                              SupplierRepository supplierRepository, ProductMapper productMapper, StockMovementService stockMovementService, UserRepository userRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.productMapper = productMapper;
        this.stockMovementService = stockMovementService;
        this.userRepository = userRepository;
    }

    @Override
    public List<ProductDto> findAllProducts() {
        List<Product> products = productRepository.findAll();
        return productMapper.toDtoList(products); //Usa el mapper
    }

    @Override
    public Page<ProductDto> findAllProducts(Pageable pageable) {
        Page<Product> products = productRepository.findAll(pageable);
        return products.map(productMapper::toDto); // Convierte Page<Product> a Page<ProductDto>
    }

    @Override
    public Optional<ProductDto> findProductById(Long id) {
        return productRepository.findById(id).map(productMapper::toDto); //Utiliza method reference
    }

    @Override
    public ProductDto saveProduct(ProductDto productDto) {
        Long categoryId = Objects.requireNonNull(productDto.getCategoryId(), "Category id is required");
        Long supplierId = Objects.requireNonNull(productDto.getSupplierId(), "Supplier id is required");

        productDto.setSku(normalizeSku(productDto.getSku()));
        productDto.setBarcode(normalizeBarcode(productDto.getBarcode()));
        validateUniqueIdentifiers(productDto.getSku(), productDto.getBarcode(), null);

        // Validar que existan la categoría y el proveedor
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category", "id", categoryId);
        }
        if (!supplierRepository.existsById(supplierId)) {
            throw new ResourceNotFoundException("Supplier", "id", supplierId);
        }

        Product product = productMapper.toEntity(productDto); // DTO -> Entidad

        // Establecer la relación bidireccional con las imágenes (MUY IMPORTANTE)
        if (product.getImages() != null) {
            for (ProductImage image : product.getImages()) {
                image.setProduct(product);
            }
        }
        Product savedProduct = productRepository.save(product); //Guardar entidad
        return productMapper.toDto(savedProduct); // Entidad -> DTO
    }

    // En ProductServiceImpl, dentro de updateProduct
    @Override
    public ProductDto updateProduct(Long id, ProductDto productDto) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        String skuToValidate = productDto.getSku() != null ? normalizeSku(productDto.getSku()) : product.getSku();
        String barcodeToValidate = productDto.getBarcode() != null
                ? normalizeBarcode(productDto.getBarcode())
                : normalizeBarcode(product.getBarcode());
        validateUniqueIdentifiers(skuToValidate, barcodeToValidate, id);
        if (productDto.getSku() != null) {
            productDto.setSku(skuToValidate);
        }
        if (productDto.getBarcode() != null) {
            productDto.setBarcode(barcodeToValidate);
        }

        // Validar que existan la categoría y el proveedor, si se proporcionaron
        if(productDto.getCategoryId() != null){
            Long categoryId = Objects.requireNonNull(productDto.getCategoryId(), "Category id cannot be null");
            if (!categoryRepository.existsById(categoryId)) {
                throw new ResourceNotFoundException("Category", "id", categoryId);
            }
            product.setCategory(categoryRepository.findById(categoryId).orElseThrow(
                    () -> new ResourceNotFoundException("Category", "id", categoryId)
            ));
        }

        if(productDto.getSupplierId() != null){
            Long supplierId = Objects.requireNonNull(productDto.getSupplierId(), "Supplier id cannot be null");
            if (!supplierRepository.existsById(supplierId)) {
                throw new ResourceNotFoundException("Supplier", "id", supplierId);
            }
            product.setSupplier(supplierRepository.findById(supplierId).orElseThrow(
                    () -> new ResourceNotFoundException("Supplier", "id", supplierId)
            ));
        }

        // Aplicar cambios del DTO sobre la entidad existente.
        productMapper.updateProductFromDto(productDto, product);

        // Limpiar imágenes antiguas y establecer el producto en las nuevas imágenes
        product.getImages().clear(); //Importante para eliminar las que ya no están
        if (productDto.getImages() != null) {
            List<ProductImage> mappedImages = productMapper.toImageEntityList(productDto.getImages());
            for (ProductImage image : mappedImages) {
                image.setProduct(product);
            }
            product.getImages().addAll(mappedImages);
        }

        Product updatedProduct = productRepository.save(product); // Guarda los cambios
        return productMapper.toDto(updatedProduct); // Devuelve el DTO actualizado
    }

    @Override
    public void deleteProduct(Long id) {
        productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        // No es necesario hacer nada especial con las imágenes, gracias a orphanRemoval=true
        productRepository.deleteById(id);
    }


    @Override
    public List<ProductDto> findProductsBelowMinStock() {
        List<Product> products = productRepository.findProductsBelowMinStock();
        return productMapper.toDtoList(products); // Convierte a DTO
    }

    @Override
    public Page<ProductDto> findProductsBelowMinStock(Pageable pageable) {
        Page<Product> products = productRepository.findProductsBelowMinStock(pageable);
        return products.map(productMapper::toDto);
    }

    @Override
    public Page<ProductDto> searchProducts(String query, Pageable pageable) {
        String normalizedQuery = query.trim();
        if (normalizedQuery.isEmpty()) {
            return findAllProducts(pageable);
        }
        return productRepository.searchByQuery(normalizedQuery, pageable).map(productMapper::toDto);
    }

    @Override
    public List<ProductDto> importProductsFromCsv(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo CSV está vacío.");
        }

        List<ProductDto> imported = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (lineNumber == 1 && trimmed.toLowerCase().contains("sku")) {
                    continue; // Salta cabecera.
                }

                String[] cols = trimmed.split(",");
                if (cols.length < 6) {
                    throw new IllegalArgumentException("Formato CSV inválido en línea " + lineNumber + ". Debe contener al menos: sku,name,sellingPrice,stock,minStock,category,supplier");
                }

                String sku = normalizeSku(cols[0]);
                String name = cols[1].trim();
                BigDecimal sellingPrice = new BigDecimal(cols[2].trim());
                int stock = Integer.parseInt(cols[3].trim());
                int minStock = Integer.parseInt(cols[4].trim());
                String categoryName = cols.length > 5 ? cols[5].trim() : "General";
                String supplierName = cols.length > 6 ? cols[6].trim() : "Suplidor General";
                BigDecimal costPrice = cols.length > 7 && !cols[7].trim().isEmpty()
                        ? new BigDecimal(cols[7].trim())
                        : sellingPrice.multiply(new BigDecimal("0.70"));
                String barcode = cols.length > 8 ? normalizeBarcode(cols[8]) : null;
                Integer unidadMedida = cols.length > 9 && !cols[9].trim().isEmpty() ? Integer.valueOf(cols[9].trim()) : 58;
                String description = cols.length > 10 ? cols[10].trim() : null;

                Category category = categoryRepository.findByNameIgnoreCase(categoryName)
                        .orElseGet(() -> {
                            Category newCategory = new Category();
                            newCategory.setName(categoryName);
                            return categoryRepository.save(newCategory);
                        });

                Supplier supplier = supplierRepository.findByNameIgnoreCase(supplierName)
                        .orElseGet(() -> {
                            Supplier newSupplier = new Supplier();
                            newSupplier.setName(supplierName);
                            newSupplier.setContactName("N/D");
                            return supplierRepository.save(newSupplier);
                        });

                ProductDto dto = new ProductDto();
                dto.setSku(sku);
                dto.setName(name);
                dto.setDescription(description);
                dto.setCategoryId(category.getId());
                dto.setSupplierId(supplier.getId());
                dto.setCostPrice(costPrice);
                dto.setSellingPrice(sellingPrice);
                dto.setStock(stock);
                dto.setMinStock(minStock);
                dto.setForSale(true);
                dto.setIndicadorFacturacion(IndicadorFacturacion.ITBIS_18);
                dto.setTipoBienServicio(TipoBienServicio.BIEN);
                dto.setUnidadMedida(unidadMedida);
                dto.setStatus(ProductStatus.ACTIVE);
                dto.setBarcode(barcode);
                dto.setTaxRate(IndicadorFacturacion.ITBIS_18.getRate());

                ProductImageDto imageDto = new ProductImageDto();
                imageDto.setUrl("placeholder-product.png");
                dto.setImages(Collections.singletonList(imageDto));

                imported.add(saveProduct(dto));
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se pudo procesar el archivo CSV.", ex);
        }

        return imported;
    }


    //Se ejecuta cada 5 minutos, puedes cambiarlo con la expresion cron
    @Scheduled(cron = "0 0/5 * * * ?") //Verifica cada 5 minutos
    public void checkProductStock() {
        logger.info("Verificando stock de productos..."); //Log
        List<ProductDto> lowStockProducts = findProductsBelowMinStock();

        if (!lowStockProducts.isEmpty()) {
            logger.warn("¡Alerta de stock bajo! Los siguientes productos están por debajo del stock mínimo:");
            for (ProductDto product : lowStockProducts) {
                logger.warn("  - ID: {}, Nombre: {}, Stock Actual: {}, Stock Mínimo: {}",
                        product.getId(), product.getName(), product.getStock(), product.getMinStock());
            }
            // Aquí podrías enviar una notificación (correo electrónico, SMS, etc.)
            // Ver sección de "Envío de Notificaciones" más abajo.
        } else {
            logger.info("Todos los productos tienen un stock adecuado.");
        }
    }


    @Override
    @Transactional
    public void adjustStock(Long productId,
                            int quantityChange,
                            StockMovementType type,
                            String reason,
                            String batchNumber,
                            LocalDateTime expirationDate,
                            BigDecimal unitCost,
                            Long sourceLocationId,
                            Long destinationLocationId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setMovementDate(LocalDateTime.now());
        movement.setQuantityChange(quantityChange);
        movement.setType(type);
        movement.setReason(reason);
        movement.setBatchNumber(batchNumber); // Lote
        movement.setExpirationDate(expirationDate); // Caducidad
        movement.setUnitCost(unitCost); //Costo unitario


        // Obtener el usuario actual (si está autenticado)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String username = userDetails.getUsername(); //Obtenemos el username

            // Si existe el usuario en BD lo asociamos; si no, evitamos romper el ajuste.
            userRepository.findByUsername(username).ifPresent(movement::setUser);
        }


        //Si es TRANSFER, se necesitan las ubicaciones
        if(type == StockMovementType.TRANSFER){
            if(sourceLocationId == null || destinationLocationId == null){
                throw new IllegalArgumentException("Source and destination locations are required for transfers.");
            }
            movement.setSourceLocationId(sourceLocationId);
            movement.setDestinationLocationId(destinationLocationId);
        }

        stockMovementService.createMovement(movement); // Guarda el movimiento, y actualiza stock
    }

    private String normalizeSku(String sku) {
        String normalized = Objects.requireNonNull(sku, "SKU is required").trim().toUpperCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("El SKU no puede estar vacío.");
        }
        return normalized;
    }

    private String normalizeBarcode(String barcode) {
        if (barcode == null) {
            return null;
        }
        String normalized = barcode.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateUniqueIdentifiers(String sku, String barcode, Long productId) {
        boolean skuExists = productId == null
                ? productRepository.existsBySkuIgnoreCase(sku)
                : productRepository.existsBySkuIgnoreCaseAndIdNot(sku, productId);
        if (skuExists) {
            throw new IllegalArgumentException("Ya existe un producto con el SKU " + sku + ".");
        }

        if (barcode != null) {
            boolean barcodeExists = productId == null
                    ? productRepository.existsByBarcodeIgnoreCase(barcode)
                    : productRepository.existsByBarcodeIgnoreCaseAndIdNot(barcode, productId);
            if (barcodeExists) {
                throw new IllegalArgumentException("Ya existe un producto con el código de barras " + barcode + ".");
            }
        }
    }

}
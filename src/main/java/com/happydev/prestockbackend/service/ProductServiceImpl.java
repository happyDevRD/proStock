package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.ProductDto;
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
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    public Page<ProductDto> findAllProducts(@NonNull Pageable pageable) {
        Page<Product> products = productRepository.findAll(pageable);
        return products.map(productMapper::toDto); // Convierte Page<Product> a Page<ProductDto>
    }

    @Override
    public Optional<ProductDto> findProductById(@NonNull Long id) {
        return productRepository.findById(id).map(productMapper::toDto); //Utiliza method reference
    }

    @Override
    public ProductDto saveProduct(@NonNull ProductDto productDto) {
        Long categoryId = Objects.requireNonNull(productDto.getCategoryId(), "Category id is required");
        Long supplierId = Objects.requireNonNull(productDto.getSupplierId(), "Supplier id is required");

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
    public ProductDto updateProduct(@NonNull Long id, @NonNull ProductDto productDto) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

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

        //Usamos el mapper para actualizar los datos.
        productMapper.toEntity(productDto); //No completo aun

        // Limpiar imágenes antiguas y establecer el producto en las nuevas imágenes
        product.getImages().clear(); //Importante para eliminar las que ya no están
        if (productDto.getImages() != null) {
            for(ProductImage image : product.getImages()){ //Este for no era necesario.
                image.setProduct(product);
            }

            // Usa el método correcto del mapper: toImageEntityList
            product.getImages().addAll(productMapper.toImageEntityList(productDto.getImages())); // AQUI ESTABA EL ERROR
        }

        Product updatedProduct = productRepository.save(product); // Guarda los cambios
        return productMapper.toDto(updatedProduct); // Devuelve el DTO actualizado
    }

    @Override
    public void deleteProduct(@NonNull Long id) {
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
    public Page<ProductDto> findProductsBelowMinStock(@NonNull Pageable pageable) {
        Page<Product> products = productRepository.findProductsBelowMinStock(pageable);
        return products.map(productMapper::toDto);
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
    public void adjustStock(@NonNull Long productId,
                            int quantityChange,
                            @NonNull StockMovementType type,
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

}
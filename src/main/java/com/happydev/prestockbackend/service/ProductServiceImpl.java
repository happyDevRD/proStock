package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.BulkProductPromoRequest;
import com.happydev.prestockbackend.dto.ProductDto;
import com.happydev.prestockbackend.dto.ProductWorkbookImportResult;
import com.happydev.prestockbackend.dto.SetupSheetRowPreview;
import com.happydev.prestockbackend.dto.WorkbookDataRow;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.mapper.ProductMapper;
import com.happydev.prestockbackend.repository.CategoryRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.SupplierRepository;
import com.happydev.prestockbackend.repository.UserRepository;
import com.happydev.prestockbackend.util.SecurityAuditUtils;
import com.happydev.prestockbackend.util.WorkbookHeaderUtils;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final AuditService auditService;

    private static final Logger logger = LoggerFactory.getLogger(ProductServiceImpl.class); //Logger


    public ProductServiceImpl(ProductRepository productRepository,
                              CategoryRepository categoryRepository,
                              SupplierRepository supplierRepository,
                              ProductMapper productMapper,
                              StockMovementService stockMovementService,
                              UserRepository userRepository,
                              AuditService auditService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.productMapper = productMapper;
        this.stockMovementService = stockMovementService;
        this.userRepository = userRepository;
        this.auditService = auditService;
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
        ProductDto saved = persistNewProduct(productDto);
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "PRODUCT_CREATED",
                "Product",
                saved.getId(),
                Map.of("sku", saved.getSku(), "name", saved.getName())
        );
        return saved;
    }

    private ProductDto persistNewProduct(@NonNull ProductDto productDto) {
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

        normalizeProductDtoStockForTipo(productDto);
        validateAndNormalizeProductPromoFields(productDto);

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

    private ProductDto persistNewProductSkipDuplicateSku(@NonNull ProductDto productDto) {
        Long categoryId = Objects.requireNonNull(productDto.getCategoryId(), "Category id is required");
        Long supplierId = Objects.requireNonNull(productDto.getSupplierId(), "Supplier id is required");

        productDto.setSku(normalizeSku(productDto.getSku()));
        productDto.setBarcode(normalizeBarcode(productDto.getBarcode()));
        if (productRepository.existsBySkuIgnoreCase(productDto.getSku())) {
            return null;
        }
        if (productDto.getBarcode() != null && productRepository.existsByBarcodeIgnoreCase(productDto.getBarcode())) {
            throw new IllegalArgumentException("Ya existe un producto con el código de barras " + productDto.getBarcode() + ".");
        }

        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category", "id", categoryId);
        }
        if (!supplierRepository.existsById(supplierId)) {
            throw new ResourceNotFoundException("Supplier", "id", supplierId);
        }

        normalizeProductDtoStockForTipo(productDto);
        validateAndNormalizeProductPromoFields(productDto);

        Product product = productMapper.toEntity(productDto);
        if (product.getImages() != null) {
            for (ProductImage image : product.getImages()) {
                image.setProduct(product);
            }
        }
        Product savedProduct = productRepository.save(product);
        return productMapper.toDto(savedProduct);
    }

    private ProductDto buildProductDtoFromImportFields(
            String skuRaw,
            String name,
            String sellingPriceRaw,
            String stockRaw,
            String minStockRaw,
            String categoryName,
            String supplierName,
            String costPriceRaw,
            String barcodeRaw,
            String unidadMedidaRaw,
            String description
    ) {
        String trimmedName = Objects.requireNonNull(name, "name").trim();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío.");
        }
        BigDecimal sellingPrice = parseImportDecimal(sellingPriceRaw, "precio de venta");
        int stock = Integer.parseInt(stockRaw.trim().replace(",", ""));
        int minStock = Integer.parseInt(minStockRaw.trim().replace(",", ""));
        String categoryResolved = categoryName != null && !categoryName.isBlank() ? categoryName.trim() : "General";
        String supplierResolved = supplierName != null && !supplierName.isBlank() ? supplierName.trim() : "Suplidor General";
        BigDecimal costPrice;
        if (costPriceRaw != null && !costPriceRaw.isBlank()) {
            costPrice = parseImportDecimal(costPriceRaw, "precio de costo");
        } else {
            costPrice = sellingPrice.multiply(new BigDecimal("0.70"));
        }
        String barcode = normalizeBarcode(barcodeRaw);
        Integer unidadMedida = unidadMedidaRaw != null && !unidadMedidaRaw.isBlank()
                ? Integer.valueOf(unidadMedidaRaw.trim().replace(",", ""))
                : 58;
        String desc = description != null && !description.isBlank() ? description.trim() : null;

        Category category = categoryRepository.findByNameIgnoreCase(categoryResolved)
                .orElseGet(() -> {
                    Category newCategory = new Category();
                    newCategory.setName(categoryResolved);
                    return categoryRepository.save(newCategory);
                });

        Supplier supplier = supplierRepository.findByNameIgnoreCase(supplierResolved)
                .orElseGet(() -> {
                    Supplier newSupplier = new Supplier();
                    newSupplier.setName(supplierResolved);
                    newSupplier.setContactName("N/D");
                    return supplierRepository.save(newSupplier);
                });

        ProductDto dto = new ProductDto();
        dto.setSku(skuRaw.trim());
        dto.setName(trimmedName);
        dto.setDescription(desc);
        dto.setCategoryId(category.getId());
        dto.setSupplierId(supplier.getId());
        dto.setCostPrice(costPrice);
        dto.setSellingPrice(sellingPrice);
        String catLower = categoryResolved.toLowerCase(Locale.ROOT);
        boolean treatAsService = catLower.contains("servicio") || catLower.contains("service");
        if (treatAsService) {
            dto.setTipoBienServicio(TipoBienServicio.SERVICIO);
            dto.setStock(0);
            dto.setMinStock(0);
        } else {
            dto.setTipoBienServicio(TipoBienServicio.BIEN);
            dto.setStock(stock);
            dto.setMinStock(minStock);
        }
        dto.setForSale(true);
        dto.setIndicadorFacturacion(IndicadorFacturacion.ITBIS_18);
        dto.setUnidadMedida(unidadMedida);
        dto.setStatus(ProductStatus.ACTIVE);
        dto.setBarcode(barcode);
        dto.setTaxRate(IndicadorFacturacion.ITBIS_18.getRate());

        dto.setImages(Collections.emptyList());
        return dto;
    }

    private static BigDecimal parseImportDecimal(String raw, String fieldLabel) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Falta " + fieldLabel + ".");
        }
        return new BigDecimal(raw.trim().replace(',', '.'));
    }

    private static String workbookCell(Map<String, String> row, String... headerAliases) {
        for (String alias : headerAliases) {
            String key = WorkbookHeaderUtils.normalizeHeaderKey(alias);
            String v = row.get(key);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    // En ProductServiceImpl, dentro de updateProduct
    @Override
    public ProductDto updateProduct(@NonNull Long id, @NonNull ProductDto productDto) {
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

        normalizeProductDtoStockForTipo(productDto);
        validateAndNormalizeProductPromoFields(productDto);

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
        ProductDto dto = productMapper.toDto(updatedProduct);
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "PRODUCT_UPDATED",
                "Product",
                id,
                Map.of("sku", dto.getSku(), "name", dto.getName())
        );
        return dto;
    }

    @Override
    public void deleteProduct(@NonNull Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        String sku = product.getSku() != null ? product.getSku() : "";
        String name = product.getName() != null ? product.getName() : "";
        productRepository.deleteById(id);
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "PRODUCT_DELETED",
                "Product",
                id,
                Map.of("sku", sku, "name", name)
        );
    }


    @Override
    public List<ProductDto> findProductsBelowMinStock() {
        List<Product> products = productRepository.findProductsBelowMinStock(TipoBienServicio.BIEN);
        return productMapper.toDtoList(products); // Convierte a DTO
    }

    @Override
    public Page<ProductDto> findProductsBelowMinStock(@NonNull Pageable pageable) {
        Page<Product> products = productRepository.findProductsBelowMinStock(TipoBienServicio.BIEN, pageable);
        return products.map(productMapper::toDto);
    }

    @Override
    public Page<ProductDto> searchProducts(@NonNull String query, @NonNull Pageable pageable) {
        String normalizedQuery = query.trim();
        if (normalizedQuery.isEmpty()) {
            return findAllProducts(pageable);
        }
        return productRepository.searchByQuery(normalizedQuery, pageable).map(productMapper::toDto);
    }

    @Override
    public ProductWorkbookImportResult importProductsFromWorkbookRows(@NonNull List<Map<String, String>> rows) {
        List<String> warnings = new ArrayList<>();
        int imported = 0;
        int skipped = 0;
        int rowIndex = 1;
        for (Map<String, String> row : rows) {
            rowIndex++;
            try {
                String sku = workbookCell(row, "sku", "codigo", "code");
                String name = workbookCell(row, "name", "nombre");
                if (sku.isEmpty() && name.isEmpty()) {
                    continue;
                }
                if (sku.isEmpty()) {
                    warnings.add("Productos fila " + rowIndex + ": falta SKU.");
                    continue;
                }
                if (name.isEmpty()) {
                    warnings.add("Productos fila " + rowIndex + ": falta nombre.");
                    continue;
                }
                String sellingPriceRaw = workbookCell(row, "sellingprice", "precioventa", "precio");
                String stockRaw = workbookCell(row, "stock");
                String minStockRaw = workbookCell(row, "minstock", "stockminimo");
                if (sellingPriceRaw.isEmpty() || stockRaw.isEmpty() || minStockRaw.isEmpty()) {
                    warnings.add("Productos fila " + rowIndex + ": faltan precio de venta, stock o stock mínimo.");
                    continue;
                }
                String categoryName = workbookCell(row, "category", "categoria");
                String supplierName = workbookCell(row, "supplier", "suplidor", "proveedor");
                String costRaw = workbookCell(row, "costprice", "costo", "preciocosto");
                String barcodeRaw = workbookCell(row, "barcode", "codigobarras", "barras");
                String umRaw = workbookCell(row, "unidadmedida", "um", "dgii_um");
                String description = workbookCell(row, "description", "descripcion");
                ProductDto dto = buildProductDtoFromImportFields(
                        sku,
                        name,
                        sellingPriceRaw,
                        stockRaw,
                        minStockRaw,
                        categoryName,
                        supplierName,
                        costRaw,
                        barcodeRaw,
                        umRaw,
                        description
                );
                ProductDto saved = persistNewProductSkipDuplicateSku(dto);
                if (saved == null) {
                    skipped++;
                } else {
                    imported++;
                }
            } catch (RuntimeException ex) {
                warnings.add("Productos fila " + rowIndex + ": " + ex.getMessage());
            }
        }
        return new ProductWorkbookImportResult(imported, skipped, warnings);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<SetupSheetRowPreview> previewProductsFromWorkbook(@NonNull List<WorkbookDataRow> rows) {
        List<SetupSheetRowPreview> out = new ArrayList<>();
        for (WorkbookDataRow wr : rows) {
            SetupSheetRowPreview one = previewOneProductRow(wr.excelRow(), wr.cells());
            if (!"SKIPPED".equals(one.status())) {
                out.add(one);
            }
        }
        return out;
    }

    private SetupSheetRowPreview previewOneProductRow(int excelRow, Map<String, String> row) {
        Map<String, String> values = new LinkedHashMap<>(row);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String sku = workbookCell(row, "sku", "codigo", "code");
        String name = workbookCell(row, "name", "nombre");
        if (sku.isEmpty() && name.isEmpty()) {
            return new SetupSheetRowPreview(excelRow, "SKIPPED", values, List.of(), List.of());
        }
        if (sku.isEmpty()) {
            errors.add("Falta SKU.");
        }
        if (name.isEmpty()) {
            errors.add("Falta nombre.");
        }
        String sellingPriceRaw = workbookCell(row, "sellingprice", "precioventa", "precio");
        String stockRaw = workbookCell(row, "stock");
        String minStockRaw = workbookCell(row, "minstock", "stockminimo");
        if (sellingPriceRaw.isEmpty() || stockRaw.isEmpty() || minStockRaw.isEmpty()) {
            errors.add("Faltan precio de venta, stock o stock mínimo.");
        }
        try {
            if (!sellingPriceRaw.isEmpty()) {
                parseImportDecimal(sellingPriceRaw, "precio de venta");
            }
            if (!stockRaw.isEmpty()) {
                Integer.parseInt(stockRaw.trim().replace(",", ""));
            }
            if (!minStockRaw.isEmpty()) {
                Integer.parseInt(minStockRaw.trim().replace(",", ""));
            }
        } catch (RuntimeException ex) {
            String m = ex.getMessage();
            errors.add(m != null && !m.isBlank() ? m : "Valor numérico inválido.");
        }
        String categoryName = workbookCell(row, "category", "categoria");
        String supplierName = workbookCell(row, "supplier", "suplidor", "proveedor");
        String categoryResolved = categoryName != null && !categoryName.isBlank() ? categoryName.trim() : "General";
        String supplierResolved = supplierName != null && !supplierName.isBlank() ? supplierName.trim() : "Suplidor General";
        if (categoryRepository.findByNameIgnoreCase(categoryResolved).isEmpty()) {
            warnings.add("La categoría \"" + categoryResolved + "\" no existe; se creará al importar.");
        }
        if (supplierRepository.findByNameIgnoreCase(supplierResolved).isEmpty()) {
            warnings.add("El suplidor \"" + supplierResolved + "\" no existe; se creará al importar.");
        }
        String barcodeRaw = workbookCell(row, "barcode", "codigobarras", "barras");
        String barcode = normalizeBarcode(barcodeRaw);
        if (!sku.isEmpty()) {
            String skuNorm = sku.trim().toUpperCase(Locale.ROOT);
            if (productRepository.existsBySkuIgnoreCase(skuNorm)) {
                warnings.add("Ya existe un producto con este SKU; la fila se omitirá al importar.");
            }
        }
        if (barcode != null && productRepository.existsByBarcodeIgnoreCase(barcode)) {
            errors.add("Ya existe otro producto con el código de barras indicado.");
        }
        String umRaw = workbookCell(row, "unidadmedida", "um", "dgii_um");
        if (umRaw != null && !umRaw.isBlank()) {
            try {
                Integer.parseInt(umRaw.trim().replace(",", ""));
            } catch (NumberFormatException e) {
                errors.add("unidadMedida debe ser un número entero.");
            }
        }
        String costRaw = workbookCell(row, "costprice", "costo", "preciocosto");
        if (costRaw != null && !costRaw.isBlank()) {
            try {
                parseImportDecimal(costRaw, "precio de costo");
            } catch (RuntimeException ex) {
                String m = ex.getMessage();
                errors.add(m != null && !m.isBlank() ? m : "Precio de costo inválido.");
            }
        }
        String status;
        if (!errors.isEmpty()) {
            status = "ERROR";
        } else if (!warnings.isEmpty()) {
            status = "WARNING";
        } else {
            status = "OK";
        }
        return new SetupSheetRowPreview(excelRow, status, values, errors, warnings);
    }

    @Override
    public List<ProductDto> importProductsFromCsv(@NonNull MultipartFile file) {
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

                ProductDto dto = buildProductDtoFromImportFields(
                        cols[0].trim(),
                        cols[1].trim(),
                        cols[2].trim(),
                        cols[3].trim(),
                        cols[4].trim(),
                        cols.length > 5 ? cols[5].trim() : "",
                        cols.length > 6 ? cols[6].trim() : "",
                        cols.length > 7 ? cols[7].trim() : "",
                        cols.length > 8 ? cols[8] : "",
                        cols.length > 9 ? cols[9].trim() : "",
                        cols.length > 10 ? cols[10].trim() : ""
                );
                imported.add(persistNewProduct(dto));
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se pudo procesar el archivo CSV.", ex);
        }

        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "PRODUCT_CSV_IMPORTED",
                "ProductImport",
                null,
                Map.of("rows", Integer.toString(imported.size()))
        );

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
    public void adjustStock(@NonNull Long productId,
                            int quantityChange,
                            @NonNull StockMovementType type,
                            @NonNull String reason,
                            String batchNumber,
                            LocalDateTime expirationDate,
                            BigDecimal unitCost,
                            Long sourceLocationId,
                            Long destinationLocationId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        if (product.getTipoBienServicio() == TipoBienServicio.SERVICIO) {
            throw new IllegalArgumentException(
                    "Los servicios no llevan inventario físico; no se pueden registrar movimientos de stock."
            );
        }

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

        String reasonShort = reason.length() > 220 ? reason.substring(0, 220) + "..." : reason;
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "STOCK_ADJUSTED",
                "Product",
                productId,
                Map.of(
                        "quantityChange", Integer.toString(quantityChange),
                        "type", type.name(),
                        "reason", reasonShort
                )
        );
    }

    @Override
    public void bulkApplyPromotionalPricing(@NonNull BulkProductPromoRequest request) {
        List<Long> ids = Objects.requireNonNull(request.getProductIds(), "productIds");
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Debe indicar al menos un producto.");
        }
        if (request.isClearPromotions()) {
            for (Long id : ids) {
                Product p = productRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
                p.setPromoPrice(null);
                p.setPromoPercentOff(null);
                p.setPromoStartDate(null);
                p.setPromoEndDate(null);
                productRepository.save(p);
            }
        } else {
            if (request.getPromoPrice() != null && request.getPromoPercentOff() != null) {
                throw new IllegalArgumentException("Indique solo precio especial o solo porcentaje de descuento, no ambos.");
            }
            if (request.getPromoPrice() == null && request.getPromoPercentOff() == null) {
                throw new IllegalArgumentException(
                        "Indique precio especial o porcentaje de descuento, o active la opción de limpiar promociones."
                );
            }
            if (request.getPromoPrice() != null && request.getPromoPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("El precio promocional debe ser mayor que cero.");
            }
            if (request.getPromoPercentOff() != null && request.getPromoPercentOff().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("El porcentaje de descuento debe ser mayor que cero.");
            }
            if (request.getPromoStartDate() != null && request.getPromoEndDate() != null
                    && request.getPromoEndDate().isBefore(request.getPromoStartDate())) {
                throw new IllegalArgumentException("La fecha fin de la promoción no puede ser anterior a la fecha inicio.");
            }
            for (Long id : ids) {
                Product p = productRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
                if (request.getPromoPrice() != null) {
                    p.setPromoPrice(request.getPromoPrice());
                    p.setPromoPercentOff(null);
                } else {
                    BigDecimal pct = request.getPromoPercentOff();
                    if (pct != null && pct.compareTo(new BigDecimal("100")) > 0) {
                        throw new IllegalArgumentException("El descuento no puede superar el 100%.");
                    }
                    p.setPromoPercentOff(pct);
                    p.setPromoPrice(null);
                }
                p.setPromoStartDate(request.getPromoStartDate());
                p.setPromoEndDate(request.getPromoEndDate());
                productRepository.save(p);
            }
        }

        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "PRODUCT_BULK_PROMO",
                "Product",
                null,
                Map.of("productCount", Integer.toString(ids.size()), "clear", Boolean.toString(request.isClearPromotions()))
        );
    }

    private static void normalizeProductDtoStockForTipo(ProductDto productDto) {
        if (productDto.getTipoBienServicio() == TipoBienServicio.SERVICIO) {
            productDto.setStock(0);
            productDto.setMinStock(0);
        }
    }

    private static void validateAndNormalizeProductPromoFields(ProductDto productDto) {
        if (productDto.getPromoStartDate() != null && productDto.getPromoEndDate() != null
                && productDto.getPromoEndDate().isBefore(productDto.getPromoStartDate())) {
            throw new IllegalArgumentException("La fecha fin de la promoción no puede ser anterior a la fecha inicio.");
        }
        boolean hasPrice = productDto.getPromoPrice() != null && productDto.getPromoPrice().compareTo(BigDecimal.ZERO) > 0;
        boolean hasPct = productDto.getPromoPercentOff() != null && productDto.getPromoPercentOff().compareTo(BigDecimal.ZERO) > 0;
        if (hasPrice && hasPct) {
            productDto.setPromoPercentOff(null);
        }
        if (productDto.getPromoPercentOff() != null) {
            if (productDto.getPromoPercentOff().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("El descuento promocional no puede superar el 100%.");
            }
            if (productDto.getPromoPercentOff().compareTo(BigDecimal.ZERO) <= 0) {
                productDto.setPromoPercentOff(null);
            }
        }
        if (productDto.getPromoPrice() != null && productDto.getPromoPrice().compareTo(BigDecimal.ZERO) <= 0) {
            productDto.setPromoPrice(null);
        }
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
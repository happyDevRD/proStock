package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.PurchaseOrderDto;
import com.happydev.prestockbackend.entity.Product;
import com.happydev.prestockbackend.entity.PurchaseOrder;
import com.happydev.prestockbackend.entity.PurchaseOrderItem;
import com.happydev.prestockbackend.entity.PurchaseOrderStatus;
import com.happydev.prestockbackend.entity.StockMovement;
import com.happydev.prestockbackend.entity.StockMovementType;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.mapper.PurchaseOrderMapper;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.PurchaseOrderRepository;
import com.happydev.prestockbackend.repository.SupplierRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;

    private final SupplierRepository supplierRepository;

    private final ProductRepository productRepository; // Para actualizar el stock

    private final PurchaseOrderMapper purchaseOrderMapper;

    private final StockMovementService stockMovementService;

    public PurchaseOrderServiceImpl(PurchaseOrderRepository purchaseOrderRepository,
                                    SupplierRepository supplierRepository,
                                    ProductRepository productRepository,
                                    PurchaseOrderMapper purchaseOrderMapper,
                                    StockMovementService stockMovementService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.stockMovementService = stockMovementService;
    }


    @Override
    public List<PurchaseOrderDto> findAllPurchaseOrders() {
        return purchaseOrderMapper.toDtoList(purchaseOrderRepository.findAll());
    }

    @Override
    public Page<PurchaseOrderDto> findAllPurchaseOrders(@NonNull Pageable pageable) {
        Page<PurchaseOrder> purchaseOrders = purchaseOrderRepository.findAll(pageable);
        return purchaseOrders.map(purchaseOrderMapper::toDto);
    }

    @Override
    public Optional<PurchaseOrderDto> findPurchaseOrderById(@NonNull Long id) {
        return purchaseOrderRepository.findById(id).map(purchaseOrderMapper::toDto);
    }

    @Override
    public PurchaseOrderDto createPurchaseOrder(@NonNull PurchaseOrderDto purchaseOrderDto) {
        //Validaciones
        Long supplierId = Objects.requireNonNull(purchaseOrderDto.getSupplierId(), "Supplier id is required");
        if (!supplierRepository.existsById(supplierId)) {
            throw new ResourceNotFoundException("Supplier", "id", supplierId);
        }

        // Convertir DTO a entidad
        PurchaseOrder purchaseOrder = purchaseOrderMapper.toEntity(purchaseOrderDto);

        //Establecer estado inicial
        purchaseOrder.setStatus(PurchaseOrderStatus.PENDING);

        // Establecer la relación bidireccional con los ítems (MUY IMPORTANTE)
        if (purchaseOrder.getItems() != null) {
            for (PurchaseOrderItem item : purchaseOrder.getItems()) {
                item.setPurchaseOrder(purchaseOrder); // Asigna la orden de compra a cada ítem
                //Validar Producto
                Long productId = Objects.requireNonNull(item.getProduct().getId());
                if(!productRepository.existsById(productId)){
                    throw new ResourceNotFoundException("Product", "id", productId);
                }
            }
        }
        // Guardar primero la orden de compra
        PurchaseOrder savedPurchaseOrder = purchaseOrderRepository.save(purchaseOrder);

        return purchaseOrderMapper.toDto(savedPurchaseOrder);
    }


    @Override
    public PurchaseOrderDto updatePurchaseOrder(@NonNull Long id, @NonNull PurchaseOrderDto purchaseOrderDto) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("PurchaseOrder", "id", id));

        //Validar Proveedor, si es que se cambia.
        if(purchaseOrderDto.getSupplierId() != null){
            Long supplierId = Objects.requireNonNull(purchaseOrderDto.getSupplierId());
            if(!supplierRepository.existsById(supplierId)){
                throw new ResourceNotFoundException("Supplier", "id", supplierId);
            }
            purchaseOrder.setSupplier(supplierRepository.findById(supplierId).orElseThrow(
                    () -> new ResourceNotFoundException("Supplier", "id", supplierId)
            ));
        }

        //Actualizar datos básicos.
        purchaseOrder.setOrderDate(purchaseOrderDto.getOrderDate());
        //purchaseOrder.setReceptionDate(purchaseOrderDto.getReceptionDate()); // No se actualiza la fecha de recepción aquí

        //Si cambia el estado.
        if(purchaseOrderDto.getStatus() != null){
            purchaseOrder.setStatus(purchaseOrderDto.getStatus());
        }

        // Actualizar items:
        // 1. Eliminar los ítems antiguos (orphanRemoval = true se encargará de esto)
        purchaseOrder.getItems().clear();

        // 2. Agregar y asociar los nuevos ítems
        if (purchaseOrderDto.getItems() != null) {
            List<PurchaseOrderItem> newItems = purchaseOrderMapper.toItemEntityList(purchaseOrderDto.getItems());
            for (PurchaseOrderItem item : newItems) {
                item.setPurchaseOrder(purchaseOrder); // Asigna la orden de compra a cada ítem

                //Validar Producto
                Long productId = Objects.requireNonNull(item.getProduct().getId());
                if(!productRepository.existsById(productId)){
                    throw new ResourceNotFoundException("Product", "id", productId);
                }
                purchaseOrder.getItems().add(item); //Agregamos a la lista

            }
        }

        PurchaseOrder updatedPurchaseOrder = purchaseOrderRepository.save(purchaseOrder); //Persistimos
        return purchaseOrderMapper.toDto(updatedPurchaseOrder);
    }

    @Override
    public void deletePurchaseOrder(@NonNull Long id) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("PurchaseOrder", "id", id));
        purchaseOrderRepository.delete(Objects.requireNonNull(purchaseOrder));
    }

    // Método para marcar una orden como recibida y actualizar el stock
    @Override
    public PurchaseOrderDto receivePurchaseOrder(@NonNull Long id) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", id));

        // Verificar que la orden esté en estado PENDING
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.PENDING) {
            throw new IllegalStateException("Cannot receive a purchase order that is not in PENDING status.");
        }

        // Registrar movimientos de entrada para mantener historial consistente
        for (PurchaseOrderItem item : purchaseOrder.getItems()) {
            Product product = item.getProduct();
            StockMovement movement = new StockMovement();
            movement.setProduct(product);
            movement.setMovementDate(LocalDateTime.now());
            movement.setQuantityChange(item.getQuantity());
            movement.setType(StockMovementType.IN);
            movement.setReason("Purchase order received");
            movement.setPurchaseOrder(purchaseOrder);
            stockMovementService.createMovement(movement);
        }

        // Cambiar el estado de la orden a RECEIVED y guardar la fecha de recepción
        purchaseOrder.setStatus(PurchaseOrderStatus.RECEIVED);
        purchaseOrder.setReceptionDate(LocalDateTime.now()); // Usar LocalDateTime
        PurchaseOrder updatedOrder = purchaseOrderRepository.save(purchaseOrder); // Guardar los cambios

        return purchaseOrderMapper.toDto(updatedOrder);
    }
}

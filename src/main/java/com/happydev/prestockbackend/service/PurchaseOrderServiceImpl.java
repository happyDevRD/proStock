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
import com.happydev.prestockbackend.repository.PurchaseOrderItemRepository;
import com.happydev.prestockbackend.repository.PurchaseOrderRepository;
import com.happydev.prestockbackend.repository.SupplierRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;

    private final PurchaseOrderItemRepository purchaseOrderItemRepository; //Para guardar los items

    private final SupplierRepository supplierRepository;

    private final ProductRepository productRepository; // Para actualizar el stock

    private final PurchaseOrderMapper purchaseOrderMapper;

    private final StockMovementService stockMovementService;

    public PurchaseOrderServiceImpl(PurchaseOrderRepository purchaseOrderRepository,
                                    PurchaseOrderItemRepository purchaseOrderItemRepository,
                                    SupplierRepository supplierRepository,
                                    ProductRepository productRepository,
                                    PurchaseOrderMapper purchaseOrderMapper,
                                    StockMovementService stockMovementService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
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
    public Page<PurchaseOrderDto> findAllPurchaseOrders(Pageable pageable) {
        Page<PurchaseOrder> purchaseOrders = purchaseOrderRepository.findAll(pageable);
        return purchaseOrders.map(purchaseOrderMapper::toDto);
    }

    @Override
    public Optional<PurchaseOrderDto> findPurchaseOrderById(Long id) {
        return purchaseOrderRepository.findById(id).map(purchaseOrderMapper::toDto);
    }

    @Override
    public PurchaseOrderDto createPurchaseOrder(PurchaseOrderDto purchaseOrderDto) {
        //Validaciones
        if (!supplierRepository.existsById(purchaseOrderDto.getSupplierId())) {
            throw new ResourceNotFoundException("Supplier", "id", purchaseOrderDto.getSupplierId());
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
                if(!productRepository.existsById(item.getProduct().getId())){
                    throw new ResourceNotFoundException("Product", "id", item.getProduct().getId());
                }
            }
        }
        // Guardar primero la orden de compra
        PurchaseOrder savedPurchaseOrder = purchaseOrderRepository.save(purchaseOrder);

        return purchaseOrderMapper.toDto(savedPurchaseOrder);
    }


    @Override
    public PurchaseOrderDto updatePurchaseOrder(Long id, PurchaseOrderDto purchaseOrderDto) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("PurchaseOrder", "id", id));

        //Validar Proveedor, si es que se cambia.
        if(purchaseOrderDto.getSupplierId() != null){
            if(!supplierRepository.existsById(purchaseOrderDto.getSupplierId())){
                throw new ResourceNotFoundException("Supplier", "id", purchaseOrderDto.getSupplierId());
            }
            purchaseOrder.setSupplier(supplierRepository.findById(purchaseOrderDto.getSupplierId()).get());
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
                if(!productRepository.existsById(item.getProduct().getId())){
                    throw new ResourceNotFoundException("Product", "id", item.getProduct().getId());
                }
                purchaseOrder.getItems().add(item); //Agregamos a la lista

            }
        }

        PurchaseOrder updatedPurchaseOrder = purchaseOrderRepository.save(purchaseOrder); //Persistimos
        return purchaseOrderMapper.toDto(updatedPurchaseOrder);
    }

    @Override
    public void deletePurchaseOrder(Long id) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("PurchaseOrder", "id", id));
        purchaseOrderRepository.delete(purchaseOrder);
    }

    // Método para marcar una orden como recibida y actualizar el stock
    @Override
    public PurchaseOrderDto receivePurchaseOrder(Long id) {
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

package com.happydev.prestockbackend.service;


import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.mapper.SaleMapper;
import com.happydev.prestockbackend.repository.CustomerRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.SaleItemRepository;
import com.happydev.prestockbackend.repository.SaleRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SaleServiceImpl implements SaleService {

    private final SaleRepository saleRepository;

    private final SaleItemRepository saleItemRepository; //Si fuera necesario.

    private final ProductRepository productRepository; // Para actualizar el stock

    private final CustomerRepository customerRepository; // Para actualizar el stock

    private final SaleMapper saleMapper;

    private final StockMovementService stockMovementService;

    public SaleServiceImpl(SaleRepository saleRepository,
                           SaleItemRepository saleItemRepository,
                           ProductRepository productRepository,
                           CustomerRepository customerRepository,
                           SaleMapper saleMapper,
                           StockMovementService stockMovementService) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.saleMapper = saleMapper;
        this.stockMovementService = stockMovementService;
    }

    @Override
    public List<SaleDto> findAllSales() {
        return saleMapper.toDtoList(saleRepository.findAll());
    }

    @Override
    public Page<SaleDto> findAllSales(Pageable pageable) {
        Page<Sale> sales = saleRepository.findAll(pageable);
        return sales.map(saleMapper::toDto);
    }

    @Override
    public Optional<SaleDto> findSaleById(Long id) {
        return saleRepository.findById(id).map(saleMapper::toDto);
    }

    @Override
    public SaleDto createSale(SaleDto saleDto) {

        //Convertir a entidad.
        Sale sale = saleMapper.toEntity(saleDto);

        //Poner la fecha actual.
        sale.setSaleDate(LocalDateTime.now());
        //Establecer estado inicial
        sale.setStatus(SaleStatus.PENDING);

        //Si se cambia el customer
        if(saleDto.getCustomerId() != null){
            Customer customer = customerRepository.findById(saleDto.getCustomerId())
                    .orElseThrow(()-> new ResourceNotFoundException("Customer", "id", saleDto.getCustomerId()));
            sale.setCustomer(customer); //Asignamos el customer.
        }

        // Establecer la relación bidireccional con los ítems (MUY IMPORTANTE)
        if (sale.getItems() != null) {
            for (SaleItem item : sale.getItems()) {
                item.setSale(sale); // Asigna la venta a cada ítem.
                //Validaciones
                if(!productRepository.existsById(item.getProduct().getId())){
                    throw new ResourceNotFoundException("Product", "id", item.getProduct().getId());
                }
            }
        }

        //Guardar en db
        Sale savedSale = saleRepository.save(sale);
        return saleMapper.toDto(savedSale);

    }

    @Override
    public SaleDto updateSale(Long id, SaleDto saleDto) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", id));

        // La fecha no se debería poder cambiar.

        // Si se cambia el customer (OBTENER EL CLIENTE)
        if (saleDto.getCustomerId() != null) {
            Customer customer = customerRepository.findById(saleDto.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", saleDto.getCustomerId()));
            sale.setCustomer(customer);
        }

        // Si cambia el estado
        if (saleDto.getStatus() != null) {
            sale.setStatus(saleDto.getStatus());
        }

        // Actualizar Items:
        // 1. Eliminar Items Antiguos:
        sale.getItems().clear();

        // 2. Agregar y asociar nuevos items
        if (saleDto.getItems() != null) {
            List<SaleItem> newItems = saleMapper.toItemEntityList(saleDto.getItems());
            for (SaleItem item : newItems) {
                item.setSale(sale);
                if (!productRepository.existsById(item.getProduct().getId())) {
                    throw new ResourceNotFoundException("Product", "id", item.getProduct().getId());
                }
                sale.getItems().add(item);
            }
        }

        // 3. Persistir
        Sale updatedSale = saleRepository.save(sale);
        return saleMapper.toDto(updatedSale);
    }

    @Override
    public void deleteSale(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", id));
        saleRepository.delete(sale); // orphanRemoval=true se encarga de los ítems
    }

    // Método para finalizar una venta y descontar el stock
    @Override
    @Transactional // Importante para que la actualización del stock sea atómica
    public SaleDto completeSale(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", id));

        // Verificar que la venta esté en estado PENDING
        if (sale.getStatus() != SaleStatus.PENDING) {
            throw new IllegalStateException("Cannot complete a sale that is not in PENDING status.");
        }

        // Registrar movimientos de stock para mantener historial consistente
        for (SaleItem item : sale.getItems()) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(()-> new ResourceNotFoundException("Product", "id", item.getProduct().getId()));

            //Verificar si hay stock
            if(product.getStock() < item.getQuantity()){
                throw new IllegalStateException("Not enough stock for product: " + product.getName());
            }

            StockMovement movement = new StockMovement();
            movement.setProduct(product);
            movement.setMovementDate(LocalDateTime.now());
            movement.setQuantityChange(-item.getQuantity());
            movement.setType(StockMovementType.OUT);
            movement.setReason("Sale completed");
            movement.setSale(sale);
            stockMovementService.createMovement(movement);

        }

        // Cambiar el estado de la venta a COMPLETED
        sale.setStatus(SaleStatus.COMPLETED);
        Sale updatedSale = saleRepository.save(sale); // Guardar los cambios

        return saleMapper.toDto(updatedSale);
    }
}
